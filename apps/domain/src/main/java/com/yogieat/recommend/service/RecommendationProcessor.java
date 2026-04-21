package com.yogieat.recommend.service;

import com.yogieat.category.domain.Category;
import com.yogieat.category.service.CategoryService;
import com.yogieat.common.GeoJson;
import com.yogieat.common.GeoUtils;
import com.yogieat.common.Region;
import com.yogieat.gathering.domain.Gathering;
import com.yogieat.gathering.domain.value.TimeSlot;
import com.yogieat.gathering.service.GatheringRepository;
import com.yogieat.participant.domain.Participant;
import com.yogieat.participant.domain.value.DistanceRange;
import com.yogieat.participant.service.ParticipantRepository;
import com.yogieat.recommend.domain.RecommendResult;
import com.yogieat.recommend.domain.RecommendResultFailed;
import com.yogieat.recommend.domain.value.CategoryScoredRestaurant;
import com.yogieat.recommend.domain.value.CategoryVoteSummary;
import com.yogieat.recommend.domain.value.DistanceScoreContext;
import com.yogieat.recommend.domain.value.FailureReason;
import com.yogieat.recommend.domain.value.PreferenceScore;
import com.yogieat.recommend.domain.value.RecommendStatus;
import com.yogieat.recommend.domain.value.RecommendationParticipantContext;
import com.yogieat.recommend.domain.value.ScoredRestaurant;
import com.yogieat.recommend.service.strategy.RecommendationSelectionStrategy;
import com.yogieat.restaurant.domain.Restaurant;
import com.yogieat.restaurant.service.RestaurantRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecommendationProcessor {

    private static final Logger log = LoggerFactory.getLogger(RecommendationProcessor.class);

    private final RecommendationScoringPolicy scoringPolicy;
    private final ParticipantRepository participantRepository;
    private final RestaurantRepository restaurantRepository;
    private final CategoryService categoryService;
    private final RecommendResultRepository recommendResultRepository;
    private final RecommendResultFailedRepository recommendResultFailedRepository;
    private final GatheringRepository gatheringRepository;
    private final RecommendationContextFactory recommendationContextFactory;
    private final RecommendationSelectionStrategy recommendationSelectionStrategy;

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

            RecommendationCandidateResult candidateResult =
                    calculateRecommendations(gatheringId, region, List.of());

            if (candidateResult.failed()) {
                saveFailedResult(
                        gatheringId,
                        candidateResult.failureReason(),
                        candidateResult.failureMessage()
                );
                return;
            }

            List<ScoredRestaurant> top3 = candidateResult.restaurants();

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

    @Transactional
    public RecommendationCandidateResult calculateRecommendations(
            Long gatheringId,
            Region region,
            List<Long> excludedRestaurantIds
    ) {
        Gathering gathering = gatheringRepository.findById(gatheringId).orElse(null);
        TimeSlot gatheringTimeSlot = gathering != null ? gathering.timeSlot() : null;

        List<Participant> participants = participantRepository.findByGatheringId(gatheringId);
        if (participants.isEmpty()) {
            return RecommendationCandidateResult.failure(
                    FailureReason.NO_PARTICIPANTS,
                    "No participants found"
            );
        }

        RecommendationParticipantContext participantContext =
                recommendationContextFactory.create(participants, scoringPolicy);

        Map<Long, Category> categoryMap = categoryService.findAll().stream()
                .collect(Collectors.toMap(Category::id, category -> category));

        Set<Long> candidateCategoryIds = buildCandidateCategoryIds(
                categoryMap,
                participantContext.categoryVoteSummary().excludedCategories()
        );
        if (candidateCategoryIds.isEmpty()) {
            return RecommendationCandidateResult.failure(
                    FailureReason.NO_RESTAURANTS,
                    "No candidate categories available after preference/dislike filtering"
            );
        }

        LocalDate scheduledDate = gathering != null ? gathering.scheduledDate() : null;
        List<Restaurant> restaurants = findRecommendationCandidates(
                region,
                candidateCategoryIds,
                gatheringTimeSlot,
                excludedRestaurantIds,
                scheduledDate
        );

        if (restaurants.isEmpty()) {
            return RecommendationCandidateResult.failure(
                    FailureReason.NO_RESTAURANTS,
                    "No restaurants found in region: " + region
            );
        }

        GeoJson.Point centerPoint = region.getCoordinatesStandard();

        List<ScoredRestaurant> top3 = findTopRestaurantsWithFallback(
                restaurants,
                categoryMap,
                participantContext,
                participants,
                centerPoint,
                gatheringTimeSlot
        );
        if (top3.isEmpty()) {
            return RecommendationCandidateResult.failure(
                    FailureReason.NO_RESTAURANTS,
                    "No suitable restaurants found after filtering"
            );
        }

        return RecommendationCandidateResult.success(top3);
    }

    private List<Restaurant> findRecommendationCandidates(
            Region region,
            Set<Long> candidateCategoryIds,
            TimeSlot gatheringTimeSlot,
            List<Long> excludedRestaurantIds,
            LocalDate scheduledDate
    ) {
        return restaurantRepository.findRecommendationCandidates(
                region,
                candidateCategoryIds,
                gatheringTimeSlot,
                excludedRestaurantIds == null ? List.of() : excludedRestaurantIds,
                scheduledDate
        );
    }

    private Set<Long> buildCandidateCategoryIds(
            Map<Long, Category> categoryMap,
            Set<String> excludedLargeCategories
    ) {
        if (categoryMap == null || categoryMap.isEmpty()) {
            return Set.of();
        }

        if (excludedLargeCategories == null || excludedLargeCategories.isEmpty()) {
            return categoryMap.values().stream()
                    .map(Category::id)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        }

        return categoryMap.values().stream()
                .filter(category -> category.id() != null)
                .filter(category -> !excludedLargeCategories.contains(
                        category.largeCategory().getDisplayName()
                ))
                .map(Category::id)
                .collect(Collectors.toSet());
    }

    /**
     * Haversine 공식을 사용한 두 좌표 간 거리 계산 (km)
     */
    private double calculateDistance(GeoJson.Point centerPoint, GeoJson.Point restaurantPoint) {
        return GeoUtils.calculateDistanceKm(centerPoint, restaurantPoint);
    }

    private boolean isWithinDistanceRange(double distance, DistanceRange range) {
        if (range == DistanceRange.ANY || range.getDistance() == null) {
            return true;
        }
        return distance <= range.getDistance();
    }

    /**
     * 다단계 Fallback 전략을 사용하여 Top 3 레스토랑 추천
     * 1단계: 선호도 점수 > 0인 레스토랑만 추천
     * 2단계: 불호 카테고리만 제외하고 모든 레스토랑 추천
     *
     * @param restaurants 전체 레스토랑 목록
     * @param categoryMap 카테고리 정보 Map
     * @param participantContext 참여자 기반 추천 컨텍스트
     * @param participants 참여자 목록
     * @param centerPoint 지역 중심 좌표
     * @param gatheringTimeSlot 모임 시간대 (LUNCH/DINNER/BOTH)
     * @return Top 3 레스토랑 목록 (점수 높은 순)
     */
    private List<ScoredRestaurant> findTopRestaurantsWithFallback(
            List<Restaurant> restaurants,
            Map<Long, Category> categoryMap,
            RecommendationParticipantContext participantContext,
            List<Participant> participants,
            GeoJson.Point centerPoint,
            TimeSlot gatheringTimeSlot) {

        int topKSize = scoringPolicy.candidate().topKSize();

        // 점수 계산은 1회만 수행하고, 필터 전략만 다르게 적용
        List<CategoryScoredRestaurant> scoredCandidates = scoreRestaurants(
                restaurants, categoryMap, participantContext,
                participants, centerPoint,
                gatheringTimeSlot
        );

        // 1단계: 선호도 점수 > 0인 레스토랑만
        List<ScoredRestaurant> primaryResults = selectTopRestaurantsByStrategy(
                scoredCandidates,
                participantContext,
                FilterStrategy.PREFERENCE_SCORE_POSITIVE
        );
        if (primaryResults.size() >= topKSize) {
            return primaryResults;
        }

        // 2단계: 불호만 필터링
        List<ScoredRestaurant> fallbackResults = selectTopRestaurantsByStrategy(
                scoredCandidates,
                participantContext,
                FilterStrategy.DISLIKED_EXCLUDED
        );

        if (primaryResults.isEmpty()) {
            return fallbackResults;
        }

        return mergeRecommendationResults(primaryResults, fallbackResults, topKSize);
    }

    /**
     * 레스토랑 점수 계산 및 필터링을 수행하여 Top 3 추출
     * (개선: Post-processing Diversification 패턴 적용, Cold Start/Freshness 부스트 추가)
     *
     * @param restaurants 전체 레스토랑 목록
     * @param categoryMap 카테고리 정보 Map
     * @param participantContext 참여자 기반 추천 컨텍스트
     * @param participants 참여자 목록
     * @param centerPoint 지역 중심 좌표
     * @param gatheringTimeSlot 모임 시간대 (LUNCH/DINNER/BOTH)
     * @return 카테고리 정보가 포함된 점수 계산 결과
     */
    private List<CategoryScoredRestaurant> scoreRestaurants(
            List<Restaurant> restaurants,
            Map<Long, Category> categoryMap,
            RecommendationParticipantContext participantContext,
            List<Participant> participants,
            GeoJson.Point centerPoint,
            TimeSlot gatheringTimeSlot) {

        int totalParticipants = participants.size();
        LocalDateTime now = LocalDateTime.now();
        List<CategoryScoredRestaurant> scoredByCategory = new ArrayList<>();
        Map<String, PreferenceScore> preferenceScoreMap = participantContext.preferenceScoreMap();
        CategoryVoteSummary categoryVoteSummary = participantContext.categoryVoteSummary();
        DistanceScoreContext distanceScoreContext = participantContext.distanceScoreContext();
        Set<String> excludedCategories = categoryVoteSummary.excludedCategories();

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
            if (excludedCategories.contains(categoryName)) {
                continue;
            }

            PreferenceScore preferenceScore = preferenceScoreMap.getOrDefault(
                    categoryName,
                    PreferenceScore.empty()
            );

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
            if (distanceScoreContext.preferredRange() != DistanceRange.ANY
                    && distanceScoreContext.effectiveDistanceBonus() > 0.0
                    && restaurant.location() != null) {
                double distance = calculateDistance(centerPoint, restaurant.location());
                if (isWithinDistanceRange(distance, distanceScoreContext.preferredRange())) {
                    baseScore += distanceScoreContext.effectiveDistanceBonus();
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
            scoredByCategory.add(new CategoryScoredRestaurant(categoryName, scored));
        }

        return scoredByCategory;
    }

    private List<ScoredRestaurant> selectTopRestaurantsByStrategy(
            List<CategoryScoredRestaurant> scoredByCategory,
            RecommendationParticipantContext participantContext,
            FilterStrategy strategy
    ) {
        int topKSize = scoringPolicy.candidate().topKSize();
        List<CategoryScoredRestaurant> baseCandidates = applyNoDislikePreferredFilter(
                scoredByCategory,
                participantContext,
                topKSize
        );

        Map<String, PreferenceScore> preferenceScoreMap = participantContext.preferenceScoreMap();
        List<CategoryScoredRestaurant> filtered = baseCandidates.stream()
                .filter(item -> shouldIncludeRestaurant(
                        preferenceScoreMap.getOrDefault(item.categoryName(), PreferenceScore.empty()),
                        strategy
                ))
                .toList();

        Map<String, Integer> quotaVotes = buildQuotaPreferenceVotes(filtered, participantContext);

        return recommendationSelectionStrategy.selectTopRestaurants(
                filtered,
                quotaVotes,
                topKSize,
                scoringPolicy.candidate().poolSize()
        );
    }

    /**
     * 카테고리 슬롯 배분용 투표값을 계산합니다.
     * - 1차: 랭크 가중 선호점수(3/2/1) - 불호 패널티(2점)를 반영한 유효표
     * - 2차: 기존 단순 선호표(하위 호환)
     * - 3차: 선호 입력이 모두 비어있는 경우 후보 카테고리 균등표
     */
    private Map<String, Integer> buildQuotaPreferenceVotes(
            List<CategoryScoredRestaurant> candidates,
            RecommendationParticipantContext participantContext
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return Map.of();
        }

        Set<String> candidateCategories = candidates.stream()
                .map(CategoryScoredRestaurant::categoryName)
                .collect(Collectors.toSet());

        Map<String, Integer> dislikeVotes = participantContext.categoryVoteSummary().dislikeVotes();
        Map<String, PreferenceScore> preferenceScoreMap = participantContext.preferenceScoreMap();

        Map<String, Integer> adjustedVotes = new HashMap<>();
        for (String category : candidateCategories) {
            PreferenceScore score = preferenceScoreMap.getOrDefault(category, PreferenceScore.empty());
            int weightedPreference = (int) Math.round(score.totalPreferenceScore());
            int dislikePenalty = dislikeVotes.getOrDefault(category, 0) * 2;
            int effectiveVotes = Math.max(0, weightedPreference - dislikePenalty);

            if (effectiveVotes > 0) {
                adjustedVotes.put(category, effectiveVotes);
            }
        }

        if (!adjustedVotes.isEmpty()) {
            return Map.copyOf(adjustedVotes);
        }

        Map<String, Integer> rawPreferenceVotes = participantContext.categoryVoteSummary().preferenceVotes().entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .filter(entry -> candidateCategories.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        if (!rawPreferenceVotes.isEmpty()) {
            return rawPreferenceVotes;
        }

        return candidateCategories.stream()
                .collect(Collectors.toMap(category -> category, category -> 1));
    }

    /**
     * 선호 카테고리 중 불호가 0표인 카테고리가 존재하면 우선 해당 카테고리를 사용합니다.
     * 단, strict 후보 수가 topK 미만이면 Top3 보장을 위해 전체 후보로 복귀합니다.
     */
    private List<CategoryScoredRestaurant> applyNoDislikePreferredFilter(
            List<CategoryScoredRestaurant> scoredByCategory,
            RecommendationParticipantContext participantContext,
            int topKSize
    ) {
        CategoryVoteSummary voteSummary = participantContext.categoryVoteSummary();
        Set<String> strictCategories = voteSummary.preferenceVotes().entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .filter(entry -> voteSummary.dislikeVotes().getOrDefault(entry.getKey(), 0) == 0)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        if (strictCategories.isEmpty()) {
            return scoredByCategory;
        }

        List<CategoryScoredRestaurant> strictCandidates = scoredByCategory.stream()
                .filter(item -> strictCategories.contains(item.categoryName()))
                .toList();

        // strict 카테고리 후보가 실제로 없으면 기존 후보를 유지
        if (strictCandidates.isEmpty()) {
            return scoredByCategory;
        }

        if (strictCandidates.size() < topKSize) {
            return scoredByCategory;
        }

        return strictCandidates;
    }


    private List<ScoredRestaurant> mergeRecommendationResults(
            List<ScoredRestaurant> primary,
            List<ScoredRestaurant> fallback,
            int topKSize) {
        List<ScoredRestaurant> merged = new ArrayList<>(topKSize);
        Set<Long> seenRestaurantIds = new HashSet<>();

        for (ScoredRestaurant scored : primary) {
            if (merged.size() >= topKSize) {
                break;
            }
            if (seenRestaurantIds.add(scored.restaurant().id())) {
                merged.add(scored);
            }
        }

        for (ScoredRestaurant scored : fallback) {
            if (merged.size() >= topKSize) {
                break;
            }
            if (seenRestaurantIds.add(scored.restaurant().id())) {
                merged.add(scored);
            }
        }

        return merged;
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
     * @param preferenceScore 해당 카테고리의 선호도 점수
     * @param strategy 필터링 전략
     * @return 추천 대상에 포함 여부
     */
    private boolean shouldIncludeRestaurant(
            PreferenceScore preferenceScore,
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
        RecommendationScoringPolicy.Credibility credibility = scoringPolicy.credibility();

        // 카카오맵 리뷰 수 가중치 (로그 스케일로 급격한 증가 방지)
        double kakaoWeight = (reviewCount != null && reviewCount > 0)
                ? Math.log10(reviewCount + 1)
                : 0.0;

        // 블로그 리뷰 가중치 (블로그 리뷰는 더 상세하므로 가중치 적용)
        double blogWeight = 0.0;
        if (blogReviewCount != null && blogReviewCount > 0) {
            blogWeight = Math.log10(blogReviewCount + 1) * credibility.blogReviewWeightMultiplier();
        }

        // 총 리뷰 가중치 (최대값으로 제한)
        double totalReviewWeight = Math.min(kakaoWeight + blogWeight, credibility.maxReviewWeight());

        // 평점 정규화 (정책의 최소/최대 평점 범위 → 0.0~1.0)
        double normalizedRating = (rating - credibility.ratingMin()) / (credibility.ratingMax() - credibility.ratingMin());
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
     * @return Cold Start 부스트 점수 (0.0 또는 정책 부스트 값)
     */
    private double calculateColdStartBoost(Restaurant restaurant, LocalDateTime now) {
        if (restaurant.createdAt() == null) {
            return 0.0;
        }
        RecommendationScoringPolicy.ColdStart coldStart = scoringPolicy.coldStart();

        boolean isNewRestaurant = ChronoUnit.DAYS.between(
                restaurant.createdAt(), now) <= coldStart.daysThreshold();
        boolean hasLowReviewCount = restaurant.reviewCount() == null
                || restaurant.reviewCount() < coldStart.reviewThreshold();
        boolean hasGoodRating = restaurant.rating() != null
                && restaurant.rating() >= coldStart.ratingThreshold();

        // 신규 맛집이면서 평점이 좋은 경우에만 부스트
        if ((isNewRestaurant || hasLowReviewCount) && hasGoodRating) {
            return coldStart.boost();
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
        RecommendationScoringPolicy.Freshness freshness = scoringPolicy.freshness();

        long daysSinceUpdate = ChronoUnit.DAYS.between(
                restaurant.updatedAt(), now);

        if (daysSinceUpdate <= freshness.recentDays()) {
            return freshness.recentBoost();  // 7일 이내: +0.3
        } else if (daysSinceUpdate <= freshness.moderateDays()) {
            return freshness.moderateBoost();  // 30일 이내: +0.1
        } else if (daysSinceUpdate >= freshness.staleDays()) {
            return freshness.stalePenalty();  // 90일 이상: -0.2
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
        RecommendationScoringPolicy.AiSummary aiSummary = scoringPolicy.aiSummary();

        double boost = 0.0;

        // 그룹 친화 키워드 매칭 (정책 임계 인원 이상일 때)
        if (participantCount >= aiSummary.groupSizeThreshold()) {
            if (containsAnyKeyword(summaryItems, aiSummary.groupKeywords())) {
                boost += aiSummary.groupBoost();
            }
        }

        // 긍정 키워드 가산
        if (containsAnyKeyword(summaryItems, aiSummary.positiveKeywords())) {
            boost += aiSummary.positiveBoost();
        }

        // 부정 키워드 감점 (웨이팅이 있으면 모임에 불편)
        if (containsAnyKeyword(summaryItems, aiSummary.negativeKeywords())) {
            boost += aiSummary.negativePenalty();
        }

        return boost;
    }

    /**
     * 문자열 리스트에 특정 키워드들 중 하나라도 포함되어 있는지 확인
     */
    private boolean containsAnyKeyword(List<String> items, List<String> keywords) {
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
