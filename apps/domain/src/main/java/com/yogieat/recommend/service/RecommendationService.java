package com.yogieat.recommend.service;

import com.yogieat.category.domain.Category;
import com.yogieat.category.domain.value.LargeCategory;
import com.yogieat.category.service.CategoryService;
import com.yogieat.common.GeoJson;
import com.yogieat.common.Region;
import com.yogieat.participant.domain.Participant;
import com.yogieat.participant.domain.value.DistanceRange;
import com.yogieat.participant.service.ParticipantAnalyzer;
import com.yogieat.participant.service.ParticipantRepository;
import com.yogieat.recommend.domain.RecommendResult;
import com.yogieat.recommend.domain.RecommendResultFailed;
import com.yogieat.recommend.domain.value.FailureReason;
import com.yogieat.recommend.domain.value.PreferenceScore;
import com.yogieat.recommend.domain.value.RecommendStatus;
import com.yogieat.recommend.domain.value.ScoredRestaurant;
import com.yogieat.restaurant.domain.Restaurant;
import com.yogieat.restaurant.service.RestaurantRepository;
import com.yogieat.util.StringUtils;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationService {
    private final ParticipantRepository participantRepository;
    private final RestaurantRepository restaurantRepository;
    private final CategoryService categoryService;
    private final RecommendResultRepository recommendResultRepository;
    private final RecommendResultFailedRepository recommendResultFailedRepository;
    private final ParticipantAnalyzer participantAnalyzer;

    @Transactional
    public void processRecommendation(Long gatheringId, Region region) {
        try {
            log.info("Processing recommendation for gathering: {}", gatheringId);

            // 1. 기존 레코드 확인
            List<RecommendResult> existing = recommendResultRepository.findByGatheringId(gatheringId);
            if (!existing.isEmpty()) {
                RecommendStatus currentStatus = existing.getFirst().status();

                // PENDING이 아닌 경우 (COMPLETED/FAILED) 재처리 방지
                if (currentStatus != RecommendStatus.PENDING) {
                    log.info("Recommendation already processed for gathering: {} with status: {}",
                             gatheringId, currentStatus);
                    return;
                }

                // PENDING인 경우: 삭제 후 진행
                log.info("Deleting PENDING status before processing for gathering: {}", gatheringId);
                recommendResultRepository.deleteByGatheringId(gatheringId);
            }

            // 2. 참여자 조회
            List<Participant> participants = participantRepository.findByGatheringId(gatheringId);
            if (participants.isEmpty()) {
                saveFailedResult(gatheringId, FailureReason.NO_PARTICIPANTS, "No participants found");
                return;
            }

            // 3. Restaurant 조회
            List<Restaurant> restaurants = restaurantRepository.findByRegion(region);
            if (restaurants.isEmpty()) {
                saveFailedResult(gatheringId, FailureReason.NO_RESTAURANTS,
                                 "No restaurants found in region: " + region);
                return;
            }

            // 4. Category 조회 및 캐싱 (Spring Cache 적용)
            Map<Long, Category> categoryMap = categoryService.findAll().stream()
                    .collect(Collectors.toMap(Category::id, category -> category));

            // 5. DistanceRange 다수결 결정
            DistanceRange majorityRange = participantAnalyzer.determineMajorityDistanceRange(participants);

            // 6. 선호도/불호 사전 집계 (성능 최적화: O(P×3) 한 번으로 O(R×P×3) 제거)
            Map<String, PreferenceScore> preferenceScoreMap = aggregatePreferenceScores(participants);

            // 6-1. 불호 카테고리 추출
            Set<String> dislikedCategories = extractDislikedCategories(participants);

            // 7. Region별 중심 좌표
            GeoJson.Point centerPoint = region.getCoordinatesStandard();

            // 8. 다단계 Fallback으로 Top 3 레스토랑 추천
            List<ScoredRestaurant> top3 = findTopRestaurantsWithFallback(
                restaurants, categoryMap, preferenceScoreMap,
                dislikedCategories,
                participants, majorityRange, centerPoint
            );

            if (top3.isEmpty()) {
                saveFailedResult(gatheringId, FailureReason.NO_RESTAURANTS,
                                 "No suitable restaurants found after filtering");
                return;
            }

            log.info("Top 3 restaurants: {}", top3.stream()
                    .map(sr -> String.format("%s(%.2f%%)", sr.restaurant().name(), sr.agreementRate()))
                    .collect(Collectors.joining(", ")));

            // 10. RecommendResult 저장 (추천 근거 텍스트 포함)
            List<RecommendResult> results = new ArrayList<>();
            for (int i = 0; i < top3.size(); i++) {
                ScoredRestaurant scored = top3.get(i);
                results.add(RecommendResult.Create.of(
                        gatheringId,
                        scored.restaurant().id(),
                        scored.agreementRate(),
                        RecommendStatus.COMPLETED,
                        i + 1, // rank: 1, 2, 3,
                        top3.get(i).totalScore(),
                        scored.reasonText()  // 추천 근거 텍스트 (신규)
                ));
            }

            recommendResultRepository.saveAll(results);
        } catch (Exception e) {
            log.error("Failed to process recommendation for gathering: {}", gatheringId, e);

            // PENDING 레코드 삭제 후 FAILED 저장
            try {
                recommendResultRepository.deleteByGatheringId(gatheringId);
                log.info("Deleted PENDING status before saving FAILED for gathering: {}", gatheringId);
            } catch (Exception deleteEx) {
                log.error("Failed to delete PENDING status for gathering: {}", gatheringId, deleteEx);
            }

            saveFailedResult(gatheringId, FailureReason.PROCESSING_EXCEPTION,
                             e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    /**
     * 참여자들의 선호도와 불호를 카테고리별로 미리 집계
     * O(P × 3) 시간에 모든 선호도 점수를 계산하여 O(R × P × 3) 반복을 제거
     * enum name ("KOREAN") 또는 displayName ("한식") 모두 처리하며, displayName으로 정규화합니다.
     */
    private Map<String, PreferenceScore> aggregatePreferenceScores(List<Participant> participants) {
        Map<String, PreferenceScore> scoreMap = new HashMap<>();

        for (Participant participant : participants) {
            // 선호도 집계
            List<String> preferences = StringUtils.splitByComma(participant.preferences());
            for (int i = 0; i < preferences.size(); i++) {
                String pref = preferences.get(i);
                if (!pref.equals("상관없음") && !pref.equals("ANY")) {
                    // enum name 또는 displayName을 displayName으로 정규화
                    String normalizedPref = normalizeToDisplayName(pref);
                    if (normalizedPref != null) {
                        PreferenceScore current = scoreMap.getOrDefault(normalizedPref, PreferenceScore.empty());
                        scoreMap.put(normalizedPref, current.addPreference(i + 1)); // rank는 1-based
                    }
                }
            }

            // 불호 집계
            List<String> dislikes = StringUtils.splitByComma(participant.dislikes());
            for (String dislike : dislikes) {
                if (!dislike.equals("상관없음") && !dislike.equals("ANY")) {
                    // enum name 또는 displayName을 displayName으로 정규화
                    String normalizedDislike = normalizeToDisplayName(dislike);
                    if (normalizedDislike != null) {
                        PreferenceScore current = scoreMap.getOrDefault(normalizedDislike, PreferenceScore.empty());
                        scoreMap.put(normalizedDislike, current.addDislike());
                    }
                }
            }
        }

        return scoreMap;
    }

    /**
     * 참여자들의 선호 카테고리를 추출합니다.
     * "상관없음"이 아닌 모든 선호 카테고리를 Set으로 반환합니다.
     *
     * @param participants 참여자 목록
     * @return 선호 카테고리 Set (비어있으면 필터링 없이 모든 카테고리 허용)
     */
    private Set<String> extractPreferredCategories(List<Participant> participants) {
        Set<String> preferredCategories = new HashSet<>();

        for (Participant participant : participants) {
            List<String> preferences = StringUtils.splitByComma(participant.preferences());
            for (String pref : preferences) {
                if (!pref.equals("상관없음")) {
                    preferredCategories.add(pref);
                }
            }
        }

        return preferredCategories;
    }

    /**
     * 참여자들의 불호 카테고리를 추출합니다.
     * "상관없음"이 아닌 모든 불호 카테고리를 Set으로 반환합니다.
     * enum name ("KOREAN") 또는 displayName ("한식") 모두 처리하며, displayName으로 정규화합니다.
     *
     * @param participants 참여자 목록
     * @return 불호 카테고리 Set (displayName 형식)
     */
    private Set<String> extractDislikedCategories(List<Participant> participants) {
        Set<String> dislikedCategories = new HashSet<>();

        for (Participant participant : participants) {
            List<String> dislikes = StringUtils.splitByComma(participant.dislikes());
            for (String dislike : dislikes) {
                if (!dislike.equals("상관없음") && !dislike.equals("ANY")) {
                    // enum name 또는 displayName을 displayName으로 정규화
                    String normalizedDislike = normalizeToDisplayName(dislike);
                    if (normalizedDislike != null) {
                        dislikedCategories.add(normalizedDislike);
                    }
                }
            }
        }

        return dislikedCategories;
    }

    /**
     * Haversine 공식을 사용한 두 좌표 간 거리 계산 (km)
     */
    private double calculateDistance(GeoJson.Point centerPoint, GeoJson.Point restaurantPoint) {
        double lat1 = centerPoint.getCoordinates().get(1);
        double lon1 = centerPoint.getCoordinates().get(0);
        double lat2 = restaurantPoint.getCoordinates().get(1);
        double lon2 = restaurantPoint.getCoordinates().get(0);

        double R = 6371; // 지구 반지름 (km)
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private boolean isWithinDistanceRange(double distance, DistanceRange range) {
        return switch (range) {
            case RANGE_500M -> distance <= 0.5;
            case RANGE_1KM -> distance <= 1.0;
            case ANY -> true;
        };
    }

    /**
     * 다단계 Fallback 전략을 사용하여 Top 3 레스토랑 추천
     * 1단계: 선호도 점수 > 0인 레스토랑만 추천
     * 2단계: 불호 카테고리만 제외하고 모든 레스토랑 추천
     *
     * @param restaurants 전체 레스토랑 목록
     * @param categoryMap 카테고리 정보 Map
     * @param preferenceScoreMap 카테고리별 선호도 점수 Map
     * @param dislikedCategories 불호 카테고리 Set
     * @param participants 참여자 목록
     * @param majorityRange 다수결 거리 범위
     * @param centerPoint 지역 중심 좌표
     * @return Top 3 레스토랑 목록 (점수 높은 순)
     */
    private List<ScoredRestaurant> findTopRestaurantsWithFallback(
            List<Restaurant> restaurants,
            Map<Long, Category> categoryMap,
            Map<String, PreferenceScore> preferenceScoreMap,
            Set<String> dislikedCategories,
            List<Participant> participants,
            DistanceRange majorityRange,
            GeoJson.Point centerPoint) {

        // 1단계: 선호도 점수 > 0인 레스토랑만
        List<ScoredRestaurant> results = scoreAndFilterRestaurants(
            restaurants, categoryMap, preferenceScoreMap,
            dislikedCategories,
            participants, majorityRange, centerPoint,
            FilterStrategy.PREFERENCE_SCORE_POSITIVE
        );

        if (!results.isEmpty()) {
            log.info("Found {} restaurants with preference score > 0", results.size());
            return results;
        }

        // 2단계: 불호만 필터링
        log.warn("No restaurants found with preference score > 0. Filtering only disliked categories...");
        results = scoreAndFilterRestaurants(
            restaurants, categoryMap, preferenceScoreMap,
            dislikedCategories,
            participants, majorityRange, centerPoint,
            FilterStrategy.DISLIKED_EXCLUDED
        );

        if (!results.isEmpty()) {
            log.info("Found {} restaurants by excluding only disliked categories", results.size());
        }

        return results;
    }

    /**
     * 레스토랑 점수 계산 및 필터링을 수행하여 Top 3 추출
     *
     * @param restaurants 전체 레스토랑 목록
     * @param categoryMap 카테고리 정보 Map
     * @param preferenceScoreMap 카테고리별 선호도 점수 Map
     * @param dislikedCategories 불호 카테고리 Set
     * @param participants 참여자 목록
     * @param majorityRange 다수결 거리 범위
     * @param centerPoint 지역 중심 좌표
     * @param strategy 필터링 전략
     * @return Top 3 레스토랑 목록 (점수 높은 순)
     */
    private List<ScoredRestaurant> scoreAndFilterRestaurants(
            List<Restaurant> restaurants,
            Map<Long, Category> categoryMap,
            Map<String, PreferenceScore> preferenceScoreMap,
            Set<String> dislikedCategories,
            List<Participant> participants,
            DistanceRange majorityRange,
            GeoJson.Point centerPoint,
            FilterStrategy strategy) {

        PriorityQueue<ScoredRestaurant> top3Heap = new PriorityQueue<>(
            Comparator.comparingDouble(ScoredRestaurant::totalScore)
        );

        int totalParticipants = participants.size();

        for (Restaurant restaurant : restaurants) {
            Category category = categoryMap.get(restaurant.categoryId());
            if (category == null) {
                continue;
            }

            String categoryName = category.largeCategory().getDisplayName();

            PreferenceScore preferenceScore = preferenceScoreMap.getOrDefault(
                categoryName,
                PreferenceScore.empty()
            );

            // 전략별 필터링 적용 (순수 선호수 기준으로 변경)
            if (!shouldIncludeRestaurant(categoryName, preferenceScore, dislikedCategories, strategy)) {
                continue;
            }

            // 점수 계산 (개선된 알고리즘)
            double totalScore = 0.0;

            // 1. 기존 선호도 점수
            totalScore += preferenceScore.calculateFinalScore();

            // 2. 순수 선호수 가산점 (신규)
            totalScore += preferenceScore.calculateNetPreferenceBonus();

            // 3. 리뷰/평점 신뢰도 점수 (신규)
            totalScore += calculateCredibilityScore(restaurant.rating(), restaurant.reviewCount());

            // 4. 거리 가산점
            if (majorityRange != DistanceRange.ANY && restaurant.location() != null) {
                double distance = calculateDistance(centerPoint, restaurant.location());
                boolean withinRange = isWithinDistanceRange(distance, majorityRange);
                if (withinRange) {
                    totalScore += 1.0;
                }
            }

            // 의견일치율 계산
            double agreementRate = getAgreementRate(participants, preferenceScore);

            // 추천 근거 텍스트 생성 (신규)
            String reasonText = buildReasonText(
                    totalParticipants,
                    preferenceScore.preferenceCount(),
                    categoryName,
                    restaurant.aiMateSummaryTitle()
            );

            ScoredRestaurant scored = new ScoredRestaurant(restaurant, totalScore, agreementRate, reasonText);

            // Top-K 알고리즘: 상위 3개만 유지
            if (top3Heap.size() < 3) {
                top3Heap.offer(scored);
            } else if (scored.totalScore() > top3Heap.peek().totalScore()) {
                top3Heap.poll();
                top3Heap.offer(scored);
            }
        }

        // 상위 3개 추출 및 정렬
        List<ScoredRestaurant> top3 = new ArrayList<>(top3Heap);
        top3.sort(Comparator.comparingDouble(ScoredRestaurant::totalScore).reversed());

        return top3;
    }

    private static double getAgreementRate(List<Participant> participants, PreferenceScore preferenceScore) {
        double preferenceOnlyScore = preferenceScore.totalPreferenceScore();
        double agreementRate;

        if (preferenceOnlyScore > 0) {
            // 선호도가 있으면 선호도 기반 계산
            double maxPossibleScore = participants.size() * 3.0;
            agreementRate = (preferenceOnlyScore / maxPossibleScore) * 100.0;
        } else {
            // 선호도가 없으면 불호 기반 계산 (수용 가능 비율)
            int dislikeCount = preferenceScore.dislikeCount();
            agreementRate = ((double)(participants.size() - dislikeCount) / participants.size()) * 100.0;
        }

        agreementRate = Math.round(agreementRate * 100.0) / 100.0;
        return agreementRate;
    }

    /**
     * 전략에 따라 레스토랑을 추천 대상에 포함할지 결정합니다.
     * (개선: 순수 선호수 기준으로 필터링)
     *
     * @param categoryName 레스토랑의 카테고리명
     * @param preferenceScore 해당 카테고리의 선호도 점수
     * @param dislikedCategories 불호 카테고리 Set
     * @param strategy 필터링 전략
     * @return 추천 대상에 포함 여부
     */
    private boolean shouldIncludeRestaurant(
            String categoryName,
            PreferenceScore preferenceScore,
            Set<String> dislikedCategories,
            FilterStrategy strategy) {

        return switch (strategy) {
            case PREFERENCE_SCORE_POSITIVE -> {
                // 선호도 점수가 0보다 큰 경우만 포함
                if (preferenceScore.totalPreferenceScore() <= 0) {
                    yield false;
                }
                // 개선: 순수 선호수 기준 필터링 (불호보다 선호가 1명 이상 적을 때만 제외)
                int netPreference = preferenceScore.getNetPreference();
                yield netPreference >= -1;  // 불호가 선호보다 2명 이상 많을 때만 제외
            }
            case DISLIKED_EXCLUDED -> {
                // 개선: 순수 선호수 기준 (불호만 있고 선호가 없는 경우만 제외)
                int netPreference = preferenceScore.getNetPreference();
                yield netPreference >= -1;  // 불호가 선호보다 2명 이상 많을 때만 제외
            }
        };
    }


    /**
     * 리뷰/평점 신뢰도 점수 계산
     * Wilson Score 기반 간소화 버전: 4.5점 100개 리뷰 > 5.0점 1개 리뷰
     *
     * @param rating 평점 (1.0~5.0)
     * @param reviewCount 리뷰 수
     * @return 신뢰도 점수
     */
    private double calculateCredibilityScore(Double rating, Integer reviewCount) {
        if (rating == null || reviewCount == null || reviewCount == 0) {
            return 0.0;
        }

        // 리뷰 수 가중치 (로그 스케일로 급격한 증가 방지)
        // 100개 → 2.0, 1000개 → 3.0
        double reviewWeight = Math.log10(reviewCount + 1);

        // 평점 정규화 (3.0~5.0 → 0.0~1.0)
        double normalizedRating = (rating - 3.0) / 2.0;
        normalizedRating = Math.max(0.0, Math.min(1.0, normalizedRating));

        // 신뢰도 점수 = 평점 × 리뷰 가중치
        return normalizedRating * reviewWeight;
    }

    /**
     * 추천 근거 텍스트 생성
     * 예: "5명 중 3명이 일식을 골라서\n400시간 숙성으로 완성한 겉바속촉 돈카츠\n를 추천해요"
     *
     * @param totalParticipants 총 참여자 수
     * @param preferenceCount 해당 카테고리 선호자 수
     * @param categoryName 카테고리명 (한글)
     * @param aiSummaryTitle AI 요약 타이틀
     * @return 추천 근거 텍스트
     */
    private String buildReasonText(
            int totalParticipants,
            int preferenceCount,
            String categoryName,
            String aiSummaryTitle) {

        StringBuilder sb = new StringBuilder();

        // 1. 참여자 선호 정보
        if (preferenceCount > 0 && totalParticipants > 1) {
            sb.append(totalParticipants).append("명 중 ")
              .append(preferenceCount).append("명이 ")
              .append(categoryName).append("을 골라서\n");
        }

        // 2. AI 요약 타이틀 (있는 경우)
        if (aiSummaryTitle != null && !aiSummaryTitle.isBlank()) {
            sb.append(aiSummaryTitle).append("\n");
        }

        // 3. 마무리 멘트
        sb.append("을(를) 추천해요");

        return sb.toString();
    }

    /**
     * 레스토랑 필터링 전략
     */
    private enum FilterStrategy {
        /** 선호도 점수 > 0인 레스토랑만 (1단계) */
        PREFERENCE_SCORE_POSITIVE,
        /** 불호 카테고리만 제외 (2단계 Fallback) */
        DISLIKED_EXCLUDED
    }

    /**
     * 카테고리 값을 displayName으로 정규화합니다.
     * enum name ("KOREAN", "CHINESE" 등) 또는 displayName ("한식", "중식" 등) 모두 처리합니다.
     *
     * @param categoryValue 카테고리 값 (enum name 또는 displayName)
     * @return displayName 형식으로 정규화된 값, 알 수 없는 값이면 null
     */
    private String normalizeToDisplayName(String categoryValue) {
        // 1. 이미 displayName이면 그대로 반환
        LargeCategory byDisplayName = LargeCategory.fromDisplayName(categoryValue);
        if (byDisplayName != null) {
            return categoryValue;
        }

        // 2. enum name이면 displayName으로 변환
        LargeCategory byEnumName = LargeCategory.fromString(categoryValue);
        if (byEnumName != null) {
            return byEnumName.getDisplayName();
        }

        // 3. 알 수 없는 값
        return null;
    }

    private void saveFailedResult(Long gatheringId, FailureReason reason, String errorMessage) {
        // 1. t_recommend_result에 FAILED 레코드 생성
        RecommendResult failedResult = RecommendResult.Create.of(
                gatheringId,
                null,
                0.0,
                RecommendStatus.FAILED,
                null,
                0.0,
                null  // reasonText
        );
        recommendResultRepository.saveAll(List.of(failedResult));

        // 2. t_recommend_result_failed에 실패 컨텍스트 저장
        RecommendResultFailed failedContext = RecommendResultFailed.Create.of(
                gatheringId,
                reason,
                errorMessage,
                LocalDateTime.now()
        );
        recommendResultFailedRepository.save(failedContext);

        log.info("Saved FAILED result with reason: {} for gathering: {}", reason, gatheringId);
    }
}
