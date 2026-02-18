package com.yogieat.recommend.service;

import com.yogieat.category.domain.Category;
import com.yogieat.category.domain.value.LargeCategory;
import com.yogieat.category.service.CategoryService;
import com.yogieat.common.GeoJson;
import com.yogieat.common.Region;
import com.yogieat.gathering.domain.Gathering;
import com.yogieat.gathering.domain.value.TimeSlot;
import com.yogieat.gathering.service.GatheringRepository;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationService {

    // ========== 점수 가중치 상수 ==========
    private static final double PREFERENCE_RANK_1_SCORE = 3.0;
    private static final double PREFERENCE_RANK_2_SCORE = 2.0;
    private static final double PREFERENCE_RANK_3_SCORE = 1.0;
    private static final double DISLIKE_PENALTY = 2.0;

    private static final double DISTANCE_BONUS = 1.0;
    private static final double DIVERSITY_BONUS = 0.5;
    private static final double NEUTRAL_PENALTY = -0.5;

    // AI 요약 부스트 상수
    private static final double AI_GROUP_BOOST = 0.5;
    private static final double AI_POSITIVE_BOOST = 0.3;
    private static final double AI_NEGATIVE_PENALTY = -0.2;
    private static final int GROUP_SIZE_THRESHOLD = 4;

    // ========== Cold Start / Freshness 상수 ==========
    private static final int COLD_START_DAYS_THRESHOLD = 30;
    private static final int COLD_START_REVIEW_THRESHOLD = 10;
    private static final double COLD_START_RATING_THRESHOLD = 4.0;
    private static final double COLD_START_BOOST = 0.3;

    private static final int FRESHNESS_RECENT_DAYS = 7;
    private static final int FRESHNESS_MODERATE_DAYS = 30;
    private static final int FRESHNESS_STALE_DAYS = 90;
    private static final double FRESHNESS_RECENT_BOOST = 0.3;
    private static final double FRESHNESS_MODERATE_BOOST = 0.1;
    private static final double FRESHNESS_STALE_PENALTY = -0.2;

    // ========== 신뢰도 점수 상수 ==========
    private static final double BLOG_REVIEW_WEIGHT_MULTIPLIER = 1.5;
    private static final double MAX_REVIEW_WEIGHT = 5.0;
    private static final double RATING_MIN = 3.0;
    private static final double RATING_MAX = 5.0;

    // ========== 후보 선정 상수 ==========
    private static final int CANDIDATE_POOL_SIZE = 10;
    private static final int TOP_K_SIZE = 3;

    private final ParticipantRepository participantRepository;
    private final RestaurantRepository restaurantRepository;
    private final CategoryService categoryService;
    private final RecommendResultRepository recommendResultRepository;
    private final RecommendResultFailedRepository recommendResultFailedRepository;
    private final ParticipantAnalyzer participantAnalyzer;
    private final GatheringRepository gatheringRepository;

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

            // 2. Gathering 조회 (TimeSlot 필터링용)
            Gathering gathering = gatheringRepository.findById(gatheringId).orElse(null);
            TimeSlot gatheringTimeSlot = gathering != null ? gathering.timeSlot() : null;

            // 3. 참여자 조회
            List<Participant> participants = participantRepository.findByGatheringId(gatheringId);
            if (participants.isEmpty()) {
                saveFailedResult(gatheringId, FailureReason.NO_PARTICIPANTS, "No participants found");
                return;
            }

            // 4. Restaurant 조회
            List<Restaurant> restaurants = restaurantRepository.findByRegion(region);
            if (restaurants.isEmpty()) {
                saveFailedResult(gatheringId, FailureReason.NO_RESTAURANTS,
                                 "No restaurants found in region: " + region);
                return;
            }

            // 5. Category 조회 및 캐싱 (Spring Cache 적용)
            Map<Long, Category> categoryMap = categoryService.findAll().stream()
                    .collect(Collectors.toMap(Category::id, category -> category));

            // 6. DistanceRange 다수결 결정
            DistanceRange majorityRange = participantAnalyzer.determineMajorityDistanceRange(participants);

            // 7. 선호도/불호 사전 집계 (성능 최적화: O(P×3) 한 번으로 O(R×P×3) 제거)
            Map<String, PreferenceScore> preferenceScoreMap = aggregatePreferenceScores(participants);

            // 7-1. 불호 카테고리 추출
            Set<String> dislikedCategories = extractDislikedCategories(participants);

            // 8. Region별 중심 좌표
            GeoJson.Point centerPoint = region.getCoordinatesStandard();

            // 9. 다단계 Fallback으로 Top 3 레스토랑 추천 (TimeSlot 필터링 포함)
            List<ScoredRestaurant> top3 = findTopRestaurantsWithFallback(
                restaurants, categoryMap, preferenceScoreMap,
                dislikedCategories,
                participants, majorityRange, centerPoint,
                gatheringTimeSlot
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
     * @param gatheringTimeSlot 모임 시간대 (LUNCH/DINNER/BOTH)
     * @return Top 3 레스토랑 목록 (점수 높은 순)
     */
    private List<ScoredRestaurant> findTopRestaurantsWithFallback(
            List<Restaurant> restaurants,
            Map<Long, Category> categoryMap,
            Map<String, PreferenceScore> preferenceScoreMap,
            Set<String> dislikedCategories,
            List<Participant> participants,
            DistanceRange majorityRange,
            GeoJson.Point centerPoint,
            TimeSlot gatheringTimeSlot) {

        // 1단계: 선호도 점수 > 0인 레스토랑만
        List<ScoredRestaurant> results = scoreAndFilterRestaurants(
            restaurants, categoryMap, preferenceScoreMap,
            dislikedCategories,
            participants, majorityRange, centerPoint,
            FilterStrategy.PREFERENCE_SCORE_POSITIVE,
            gatheringTimeSlot
        );

        if (!results.isEmpty()) {
            return results;
        }

        // 2단계: 불호만 필터링
        results = scoreAndFilterRestaurants(
            restaurants, categoryMap, preferenceScoreMap,
            dislikedCategories,
            participants, majorityRange, centerPoint,
            FilterStrategy.DISLIKED_EXCLUDED,
            gatheringTimeSlot
        );

        return results;
    }

    /**
     * 레스토랑 점수 계산 및 필터링을 수행하여 Top 3 추출
     * (개선: Post-processing Diversification 패턴 적용, Cold Start/Freshness 부스트 추가)
     *
     * @param restaurants 전체 레스토랑 목록
     * @param categoryMap 카테고리 정보 Map
     * @param preferenceScoreMap 카테고리별 선호도 점수 Map
     * @param dislikedCategories 불호 카테고리 Set
     * @param participants 참여자 목록
     * @param majorityRange 다수결 거리 범위
     * @param centerPoint 지역 중심 좌표
     * @param strategy 필터링 전략
     * @param gatheringTimeSlot 모임 시간대 (LUNCH/DINNER/BOTH)
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
            FilterStrategy strategy,
            TimeSlot gatheringTimeSlot) {

        // 1단계: 다양성 부스트 없이 기본 점수로 상위 후보 선정
        PriorityQueue<ScoredRestaurant> candidateHeap = new PriorityQueue<>(
            Comparator.comparingDouble(ScoredRestaurant::totalScore)
        );

        int totalParticipants = participants.size();
        LocalDateTime now = LocalDateTime.now();

        for (Restaurant restaurant : restaurants) {
            // TimeSlot 필터링 (최우선)
            if (!isTimeSlotCompatible(gatheringTimeSlot, restaurant.timeSlot())) {
                continue;
            }

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

            // 기본 점수 계산 (다양성 부스트 제외)
            double baseScore = 0.0;

            // 1. 기존 선호도 점수
            baseScore += preferenceScore.calculateFinalScore();

            // 2. 순수 선호수 가산점
            baseScore += preferenceScore.calculateNetPreferenceBonus();

            // 3. 리뷰/평점 신뢰도 점수
            baseScore += calculateCredibilityScore(
                    restaurant.rating(),
                    restaurant.reviewCount(),
                    restaurant.blogReviewCount()
            );

            // 4. 거리 가산점
            if (majorityRange != DistanceRange.ANY && restaurant.location() != null) {
                double distance = calculateDistance(centerPoint, restaurant.location());
                if (isWithinDistanceRange(distance, majorityRange)) {
                    baseScore += DISTANCE_BONUS;
                }
            }

            // 의견일치율 계산
            double agreementRate = getAgreementRate(participants, preferenceScore);

            // 5. 의견일치율 가산점 (0~100% → 0~1점)
            baseScore += agreementRate / 100.0;

            // 6. AI 요약 부스트 점수
            baseScore += calculateAiSummaryBoost(
                    restaurant.aiMateSummaryContents(),
                    totalParticipants
            );

            // 7. Cold Start 부스트 (신규 맛집 지원)
            baseScore += calculateColdStartBoost(restaurant, now);

            // 8. Freshness 부스트 (정보 신선도)
            baseScore += calculateFreshnessBoost(restaurant, now);

            // 소수점 셋째 자리 반올림
            baseScore = roundToThreeDecimals(baseScore);

            // 추천 근거 텍스트 생성
            String reasonText = buildReasonText(
                    totalParticipants,
                    preferenceScore.preferenceCount(),
                    categoryName,
                    restaurant.aiMateSummaryTitle()
            );

            ScoredRestaurant scored = new ScoredRestaurant(restaurant, baseScore, agreementRate, reasonText);

            // 상위 후보만 유지 (CANDIDATE_POOL_SIZE개)
            if (candidateHeap.size() < CANDIDATE_POOL_SIZE) {
                candidateHeap.offer(scored);
            } else if (scored.totalScore() > candidateHeap.peek().totalScore()) {
                candidateHeap.poll();
                candidateHeap.offer(scored);
            }
        }

        // 2단계: 후보 중에서 다양성을 고려하여 최종 Top 3 선정 (Post-processing Diversification)
        List<ScoredRestaurant> candidates = new ArrayList<>(candidateHeap);
        candidates.sort(Comparator.comparingDouble(ScoredRestaurant::totalScore).reversed());

        return selectTop3WithDiversity(candidates);
    }


    /**
     * 후보 중에서 다양성을 고려하여 Top 3 선정 (MMR-style Greedy Selection)
     *
     * <p>각 슬롯마다 남은 전체 후보에 현재 선정 컨텍스트 기반 다양성 부스트를 적용하고,
     * 그 중 최고 점수를 가진 후보를 선택한다. 이를 통해 낮은 base score를 가진 후보라도
     * 다양성 부스트를 통해 상위권으로 진입할 수 있다.
     *
     * @param candidates 점수 내림차순 정렬된 후보 목록
     * @return 다양성이 고려된 Top 3
     */
    private List<ScoredRestaurant> selectTop3WithDiversity(List<ScoredRestaurant> candidates) {
        if (candidates.size() <= TOP_K_SIZE) {
            return candidates;
        }

        List<ScoredRestaurant> remaining = new ArrayList<>(candidates);
        List<ScoredRestaurant> result = new ArrayList<>();
        Set<String> selectedMenuTypes = new HashSet<>();

        while (result.size() < TOP_K_SIZE && !remaining.isEmpty()) {
            // 매 슬롯마다 현재 선정 컨텍스트 기준으로 모든 후보 재평가
            int bestIndex = 0;
            double bestScore = Double.NEGATIVE_INFINITY;

            for (int i = 0; i < remaining.size(); i++) {
                ScoredRestaurant candidate = remaining.get(i);
                String menuType = classifyMenuType(candidate.restaurant().representMenu());
                double diversityBoost = selectedMenuTypes.contains(menuType) ? 0.0 : DIVERSITY_BONUS;
                double boostedScore = roundToThreeDecimals(candidate.totalScore() + diversityBoost);

                if (boostedScore > bestScore) {
                    bestScore = boostedScore;
                    bestIndex = i;
                }
            }

            ScoredRestaurant selected = remaining.remove(bestIndex);
            String selectedMenuType = classifyMenuType(selected.restaurant().representMenu());

            result.add(new ScoredRestaurant(
                    selected.restaurant(),
                    bestScore,
                    selected.agreementRate(),
                    selected.reasonText()
            ));
            selectedMenuTypes.add(selectedMenuType);
        }

        result.sort(Comparator.comparingDouble(ScoredRestaurant::totalScore).reversed());
        return result;
    }

    private static double getAgreementRate(List<Participant> participants, PreferenceScore preferenceScore) {
        int totalParticipants = participants.size();

        if (totalParticipants == 0) {
            return 0.0;
        }

        // 의견 일치율 = 해당 카테고리를 선택한 참여자 비율
        int preferenceCount = preferenceScore.preferenceCount();
        double agreementRate = ((double) preferenceCount / totalParticipants) * 100.0;

        // 소수점 둘째 자리 반올림
        return Math.round(agreementRate * 100.0) / 100.0;
    }


    /**
     * TimeSlot 호환성 체크
     * 모임의 TimeSlot과 맛집의 TimeSlot이 호환되는지 확인
     *
     * @param gatheringSlot 모임의 TimeSlot
     * @param restaurantSlot 맛집의 TimeSlot
     * @return 호환 여부 (true: 추천 대상, false: 필터링)
     */
    private boolean isTimeSlotCompatible(TimeSlot gatheringSlot, TimeSlot restaurantSlot) {
        // null이면 필터링 안함 (하위 호환성)
        if (gatheringSlot == null || restaurantSlot == null) {
            return true;
        }

        // 맛집이 BOTH면 항상 호환
        if (restaurantSlot == TimeSlot.BOTH) {
            return true;
        }

        // 모임이 BOTH면 모두 허용
        if (gatheringSlot == TimeSlot.BOTH) {
            return true;
        }

        // 같은 TimeSlot만 호환
        return gatheringSlot == restaurantSlot;
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
     * 리뷰/평점 신뢰도 점수 계산 (개선: 블로그 리뷰 반영)
     * Wilson Score 기반 간소화 버전: 4.5점 100개 리뷰 > 5.0점 1개 리뷰
     * 블로그 리뷰는 일반 리뷰보다 상세하므로 1.5배 가중치 적용
     *
     * @param rating 평점 (1.0~5.0)
     * @param reviewCount 카카오맵 리뷰 수
     * @param blogReviewCount 블로그 리뷰 수
     * @return 신뢰도 점수
     */
    private double calculateCredibilityScore(Double rating, Integer reviewCount, Integer blogReviewCount) {
        if (rating == null || ((reviewCount == null || reviewCount == 0) && (blogReviewCount == null || blogReviewCount == 0))) {
            return 0.0;
        }

        // 카카오맵 리뷰 수 가중치 (로그 스케일로 급격한 증가 방지)
        double kakaoWeight = (reviewCount != null && reviewCount > 0)
                ? Math.log10(reviewCount + 1)
                : 0.0;

        // 블로그 리뷰 가중치 (블로그 리뷰는 더 상세하므로 가중치 적용)
        double blogWeight = 0.0;
        if (blogReviewCount != null && blogReviewCount > 0) {
            blogWeight = Math.log10(blogReviewCount + 1) * BLOG_REVIEW_WEIGHT_MULTIPLIER;
        }

        // 총 리뷰 가중치 (최대값으로 제한)
        double totalReviewWeight = Math.min(kakaoWeight + blogWeight, MAX_REVIEW_WEIGHT);

        // 평점 정규화 (RATING_MIN~RATING_MAX → 0.0~1.0)
        double normalizedRating = (rating - RATING_MIN) / (RATING_MAX - RATING_MIN);
        normalizedRating = Math.max(0.0, Math.min(1.0, normalizedRating));

        // 신뢰도 점수 = 평점 × 총 리뷰 가중치
        return normalizedRating * totalReviewWeight;
    }


    /**
     * Cold Start 부스트 점수 계산
     * 신규 맛집(등록 30일 이내 또는 리뷰 10개 미만)에 가산점 부여
     * 단, 평점이 4.0 이상인 경우에만 부스트 적용 (잠재력 있는 신규)
     *
     * @param restaurant 레스토랑 정보
     * @param now 현재 시간
     * @return Cold Start 부스트 점수 (0.0 또는 COLD_START_BOOST)
     */
    private double calculateColdStartBoost(Restaurant restaurant, LocalDateTime now) {
        if (restaurant.createdAt() == null) {
            return 0.0;
        }

        boolean isNewRestaurant = ChronoUnit.DAYS.between(
                restaurant.createdAt(), now) <= COLD_START_DAYS_THRESHOLD;
        boolean hasLowReviewCount = restaurant.reviewCount() == null
                || restaurant.reviewCount() < COLD_START_REVIEW_THRESHOLD;
        boolean hasGoodRating = restaurant.rating() != null
                && restaurant.rating() >= COLD_START_RATING_THRESHOLD;

        // 신규 맛집이면서 평점이 좋은 경우에만 부스트
        if ((isNewRestaurant || hasLowReviewCount) && hasGoodRating) {
            return COLD_START_BOOST;
        }

        return 0.0;
    }

    /**
     * Freshness 부스트 점수 계산
     * 최근 업데이트된 맛집에 가산점, 오래된 정보에 페널티
     *
     * @param restaurant 레스토랑 정보
     * @param now 현재 시간
     * @return Freshness 부스트 점수 (-0.2 ~ +0.3)
     */
    private double calculateFreshnessBoost(Restaurant restaurant, LocalDateTime now) {
        if (restaurant.updatedAt() == null) {
            return 0.0;
        }

        long daysSinceUpdate = ChronoUnit.DAYS.between(
                restaurant.updatedAt(), now);

        if (daysSinceUpdate <= FRESHNESS_RECENT_DAYS) {
            return FRESHNESS_RECENT_BOOST;  // 7일 이내: +0.3
        } else if (daysSinceUpdate <= FRESHNESS_MODERATE_DAYS) {
            return FRESHNESS_MODERATE_BOOST;  // 30일 이내: +0.1
        } else if (daysSinceUpdate >= FRESHNESS_STALE_DAYS) {
            return FRESHNESS_STALE_PENALTY;  // 90일 이상: -0.2
        }

        return 0.0;  // 30~90일: 0점
    }

    /**
     * AI 요약 기반 부스트 점수 계산
     * aiMateSummaryContents 키워드 리스트로 그룹 특성과 매칭
     *
     * @param summaryItems AI 요약 항목 리스트
     * @param participantCount 참여자 수
     * @return 부스트 점수 (-0.2 ~ +0.8)
     */
    private double calculateAiSummaryBoost(List<String> summaryItems, int participantCount) {
        if (summaryItems == null || summaryItems.isEmpty()) {
            return 0.0;
        }

        double boost = 0.0;

        // 그룹 친화 키워드 매칭 (GROUP_SIZE_THRESHOLD명 이상일 때)
        if (participantCount >= GROUP_SIZE_THRESHOLD) {
            if (containsAnyKeyword(summaryItems, "단체석", "대형 테이블", "모임", "단체")) {
                boost += AI_GROUP_BOOST;
            }
        }

        // 긍정 키워드 가산
        if (containsAnyKeyword(summaryItems, "추천", "인기", "맛집", "특별", "유명")) {
            boost += AI_POSITIVE_BOOST;
        }

        // 부정 키워드 감점 (웨이팅이 있으면 모임에 불편)
        if (containsAnyKeyword(summaryItems, "웨이팅 필수", "예약 필수", "대기 시간")) {
            boost += AI_NEGATIVE_PENALTY;
        }

        return boost;
    }

    /**
     * 문자열 리스트에 특정 키워드들 중 하나라도 포함되어 있는지 확인
     */
    private boolean containsAnyKeyword(List<String> items, String... keywords) {
        for (String item : items) {
            for (String keyword : keywords) {
                if (item.contains(keyword)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 메뉴 다양성 부스트 점수 계산
     * Top 3 추천 시 메뉴 타입이 중복되지 않도록 다양성 확보
     *
     * @param representMenu 대표 메뉴
     * @param alreadyRecommendedMenuTypes 이미 추천된 메뉴 타입 Set
     * @return 다양성 점수 (0.0 or 0.5)
     */
    private double calculateMenuDiversityBoost(String representMenu, Set<String> alreadyRecommendedMenuTypes) {
        if (representMenu == null || representMenu.isBlank()) {
            return 0.0;
        }

        String menuType = classifyMenuType(representMenu);

        // 이미 추천된 메뉴 타입이 아니면 가산점
        if (!alreadyRecommendedMenuTypes.contains(menuType)) {
            return 0.5;
        }

        return 0.0;
    }

    /**
     * 메뉴를 타입별로 분류
     * 키워드 기반 단순 분류
     */
    private String classifyMenuType(String menu) {
        if (menu == null || menu.isBlank()) {
            return "기타";
        }

        String lowerMenu = menu.toLowerCase();

        // 튀김류
        if (lowerMenu.contains("돈카츠") || lowerMenu.contains("카츠") || lowerMenu.contains("튀김")
                || lowerMenu.contains("가라아게") || lowerMenu.contains("텐동")) {
            return "튀김류";
        }
        // 탕/국류
        if (lowerMenu.contains("국밥") || lowerMenu.contains("탕") || lowerMenu.contains("찌개")
                || lowerMenu.contains("전골") || lowerMenu.contains("샤브샤브")) {
            return "탕류";
        }
        // 면류
        if (lowerMenu.contains("면") || lowerMenu.contains("라멘") || lowerMenu.contains("우동")
                || lowerMenu.contains("파스타") || lowerMenu.contains("짬뽕") || lowerMenu.contains("냉면")
                || lowerMenu.contains("칼국수") || lowerMenu.contains("소바")) {
            return "면류";
        }
        // 구이류
        if (lowerMenu.contains("구이") || lowerMenu.contains("삼겹살") || lowerMenu.contains("갈비")
                || lowerMenu.contains("스테이크") || lowerMenu.contains("바베큐") || lowerMenu.contains("불고기")) {
            return "구이류";
        }
        // 회/생선류
        if (lowerMenu.contains("회") || lowerMenu.contains("사시미") || lowerMenu.contains("스시")
                || lowerMenu.contains("초밥") || lowerMenu.contains("오마카세")) {
            return "회류";
        }
        // 볶음류
        if (lowerMenu.contains("볶음") || lowerMenu.contains("덮밥") || lowerMenu.contains("비빔")) {
            return "볶음류";
        }
        // 빵/디저트류
        if (lowerMenu.contains("빵") || lowerMenu.contains("케이크") || lowerMenu.contains("디저트")
                || lowerMenu.contains("브런치")) {
            return "디저트류";
        }
        // 피자/양식
        if (lowerMenu.contains("피자") || lowerMenu.contains("버거") || lowerMenu.contains("햄버거")) {
            return "양식류";
        }
        // 치킨
        if (lowerMenu.contains("치킨") || lowerMenu.contains("닭")) {
            return "치킨류";
        }

        return "기타";
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


    /**
     * 소수점 셋째 자리 반올림 유틸리티
     */
    private static double roundToThreeDecimals(double value) {
        return Math.round(value * 1000.0) / 1000.0;
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
