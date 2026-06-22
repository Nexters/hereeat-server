package com.yogieat.recommend.service;

import com.yogieat.category.domain.Category;
import com.yogieat.category.service.CategoryService;
import com.yogieat.common.Region;
import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import com.yogieat.gathering.domain.Gathering;
import com.yogieat.gathering.service.GatheringService;
import com.yogieat.participant.domain.Participant;
import com.yogieat.participant.domain.value.DistanceRange;
import com.yogieat.participant.service.ParticipantAnalyzer;
import com.yogieat.participant.service.ParticipantService;
import com.yogieat.recommend.domain.RecommendRerollHistory;
import com.yogieat.recommend.domain.RecommendResult;
import com.yogieat.recommend.domain.command.RecommendCommand;
import com.yogieat.recommend.domain.result.RecommendResultData;
import com.yogieat.recommend.domain.value.CategoryAggregation;
import com.yogieat.recommend.domain.value.RecommendStatus;
import com.yogieat.recommend.domain.value.ScoredRestaurant;
import com.yogieat.recommend.event.RecommendResultCreatedEvent;
import com.yogieat.restaurant.domain.Restaurant;
import com.yogieat.restaurant.service.RestaurantService;
import com.yogieat.util.LockManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecommendResultFacade {

    private static final Logger log = LoggerFactory.getLogger(RecommendResultFacade.class);

    /** v1 조회는 대표 추천(상위 1개 + 그 외 2개)만 노출한다. */
    private static final int V1_RANKING_SIZE = 3;

    private final GatheringService gatheringService;
    private final RecommendResultService recommendResultService;
    private final RestaurantService restaurantService;
    private final CategoryService categoryService;
    private final ParticipantService participantService;
    private final ParticipantAnalyzer participantAnalyzer;
    private final LockManager lockManager;
    private final ApplicationEventPublisher eventPublisher;
    private final RecommendValidator recommendValidator;
    private final RecommendationProcessor recommendationProcessor;
    private final RecommendRerollHistoryService recommendRerollHistoryService;

    @Transactional(readOnly = true)
    public RecommendResultData.Get getRecommendResults(String accessKey) {
        // v1: 대표 추천 상위 3개만 노출
        return buildRecommendResults(accessKey, V1_RANKING_SIZE);
    }

    /**
     * accessKey 기준으로 저장된 추천 결과를 조회해 응답을 구성한다.
     * 추천 생성 시점(RecommendationProcessor)에 1~N위를 모두 적재하므로 조회는 단순 읽기로 처리한다.
     *
     * @param maxRankingSize 응답에 포함할 최대 추천 수 (rank 오름차순 기준 상위 N개)
     */
    private RecommendResultData.Get buildRecommendResults(String accessKey, int maxRankingSize) {
        // 1. accessKey로 Gathering 조회
        Gathering gathering = gatheringService.getGatheringByAccessKey(accessKey);
        RecommendResultData.GatheringInfo gatheringInfo = RecommendResultData.GatheringInfo.of(gathering);

        // 2. gatheringId로 RecommendResult 목록 조회 (rank 순서대로)
        List<RecommendResult> recommendResults = recommendResultService.findByGatheringId(gathering.id());

        // 3. 결과가 없는 경우
        if (recommendResults.isEmpty()) {
            return RecommendResultData.Get.ofEmpty(gatheringInfo);
        }

        // 4. PENDING / FAILED 상태인 경우
        RecommendStatus firstStatus = recommendResults.getFirst().status();
        if (firstStatus == RecommendStatus.PENDING) {
            return RecommendResultData.Get.ofPending(gatheringInfo);
        }
        if (firstStatus == RecommendStatus.FAILED) {
            return RecommendResultData.Get.ofFailed(gatheringInfo);
        }

        // 4-1. 노출 개수 제한 (rank 오름차순 기준 상위 N개)
        List<RecommendResult> displayResults = recommendResults.stream()
                .sorted(Comparator.comparing(
                        RecommendResult::rank,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ))
                .limit(maxRankingSize)
                .toList();

        // 5. 참여자 목록 조회
        List<Participant> participants = participantService.getByGatheringId(gathering.id());

        // 6. 카테고리별 선호도/불호 집계
        CategoryAggregation aggregation = participantAnalyzer.aggregateCategoryPreferences(participants);

        // 6-1. DistanceRange별 집계
        Map<String, Integer> distances = participantAnalyzer.aggregateDistanceRanges(participants);

        // 7. Restaurant 정보와 Category 정보 조회 및 캐싱
        List<Long> restaurantIds = displayResults.stream()
                .map(RecommendResult::restaurantId)
                .toList();
        Map<Long, Restaurant> restaurantMap = restaurantService.findByIds(restaurantIds).stream()
                .collect(Collectors.toMap(Restaurant::id, Function.identity()));
        Map<Long, Category> categoryMap = categoryService.findAll().stream()
                .collect(Collectors.toMap(Category::id, Function.identity()));

        // 8. 저장된 추천 결과 빌드
        List<RecommendResultData.Ranking> rankings = displayResults.stream()
                .map(result -> buildRankingResult(result, restaurantMap, categoryMap, gathering.region()))
                .toList();

        // 9. 평균 의견 일치율 계산 (노출 결과 기준, 소수점 둘째자리 반올림)
        double averageAgreementRate = calculateAverageAgreementRate(displayResults);

        return RecommendResultData.Get.of(
                RecommendStatus.COMPLETED,
                rankings,
                aggregation.preferences(),
                aggregation.dislikes(),
                distances,
                averageAgreementRate,
                gatheringInfo
        );
    }

    private double calculateAverageAgreementRate(List<RecommendResult> recommendResults) {
        return Math.round(recommendResults.stream()
                .map(RecommendResult::agreementRate)
                .filter(rate -> rate != null)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0) * 100.0) / 100.0;
    }

    @Transactional(readOnly = true)
    public RecommendResultData.Get getRecommendResultsV2(String accessKey) {
        // v2: 저장된 추천 결과(최대 resultSize, 기본 9개)를 모두 노출
        return buildRecommendResults(accessKey, Integer.MAX_VALUE);
    }

    @Transactional
    public void proceedRecommendation(RecommendCommand.Proceed command) {
        lockManager.executeWithLock(command.accessKey(), () -> {
            // 1. Gathering 조회 (없음/삭제 시 예외 자동 발생)
            Gathering gathering = gatheringService.getGatheringByAccessKey(command.accessKey());

            // 2. 현재 참여자 수 조회
            long currentCount = participantService.countByGatheringId(gathering.id());

            // 3. 과반수 조건 검증 (currentCount * 2 >= peopleCount)
            recommendValidator.validateMajorityReached(currentCount, gathering.peopleCount());

            // 4. 이미 추천 진행 중 또는 완료된 경우 중복 방지
            recommendValidator.validateNotAlreadyProceeded(
                    recommendResultService.existsByGatheringId(gathering.id()));

            // 5. PENDING 상태 생성 및 이벤트 발행
            recommendResultService.createPendingStatus(gathering.id());
            log.info("Publishing RecommendResultCreatedEvent by majority for gathering: {}", gathering.id());
            eventPublisher.publishEvent(RecommendResultCreatedEvent.of(
                    this, gathering.id(), gathering.region(), gathering.peopleCount(), command.accessKey(), currentCount
            ));
            return null;
        });
    }

    @Transactional
    public RecommendResultData.Reroll rerollRecommendResults(RecommendCommand.Reroll command) {
        Gathering gathering = gatheringService.getGatheringByAccessKeyForUpdate(command.accessKey());
        List<RecommendResult> recommendResults = recommendResultService.findByGatheringId(gathering.id());

        recommendValidator.validateRerollAvailable(recommendResults);

        List<Long> excludedRestaurantIds = command.restaurantIds() == null
                ? List.of()
                : command.restaurantIds().stream()
                        .filter(id -> id != null && id > 0)
                        .distinct()
                        .toList();

        RecommendationCandidateResult candidateResult = recommendationProcessor.calculateRecommendations(
                gathering.id(),
                gathering.region(),
                excludedRestaurantIds
        );

        RecommendResultData.Reroll rerollResult;

        if (candidateResult.failed() || candidateResult.restaurants().isEmpty()) {
            rerollResult = RecommendResultData.Reroll.of(List.of());
        } else {
            List<Long> restaurantIdsByRank = candidateResult.restaurants().stream()
                    .map(ScoredRestaurant::restaurant)
                    .map(Restaurant::id)
                    .toList();
            Map<Long, Restaurant> restaurantMap = restaurantService.findByIds(restaurantIdsByRank).stream()
                    .collect(Collectors.toMap(Restaurant::id, Function.identity()));
            Map<Long, Category> categoryMap = categoryService.findAll().stream()
                    .collect(Collectors.toMap(Category::id, Function.identity()));

            rerollResult = RecommendResultData.Reroll.of(
                    buildRankingResults(candidateResult.restaurants(), restaurantMap, categoryMap, gathering.region())
            );
        }

        recommendRerollHistoryService.create(
                gathering.id(),
                excludedRestaurantIds,
                toRerollHistoryResults(candidateResult.restaurants())
        );
        return rerollResult;
    }

    private List<RecommendRerollHistory.Result> toRerollHistoryResults(List<ScoredRestaurant> scoredRestaurants) {
        if (scoredRestaurants == null || scoredRestaurants.isEmpty()) {
            return List.of();
        }

        List<RecommendRerollHistory.Result> results = new ArrayList<>();
        for (int i = 0; i < scoredRestaurants.size(); i++) {
            ScoredRestaurant scoredRestaurant = scoredRestaurants.get(i);
            results.add(RecommendRerollHistory.Result.of(
                    i + 1,
                    scoredRestaurant.restaurant().id(),
                    scoredRestaurant.agreementRate(),
                    scoredRestaurant.reasonText()
            ));
        }

        return List.copyOf(results);
    }

    private RecommendResultData.Ranking buildRankingResult(
            RecommendResult result,
            Map<Long, Restaurant> restaurantMap,
            Map<Long, Category> categoryMap,
            Region region) {
        Restaurant restaurant = restaurantMap.get(result.restaurantId());
        return buildRankingResult(result.rank(), result.reasonText(), restaurant, categoryMap, region);
    }

    private List<RecommendResultData.Ranking> buildRankingResults(
            List<ScoredRestaurant> scoredRestaurants,
            Map<Long, Restaurant> restaurantMap,
            Map<Long, Category> categoryMap,
            Region region
    ) {
        List<RecommendResultData.Ranking> rankings = new ArrayList<>();
        for (int i = 0; i < scoredRestaurants.size(); i++) {
            ScoredRestaurant scoredRestaurant = scoredRestaurants.get(i);
            Restaurant restaurant = restaurantMap.get(scoredRestaurant.restaurant().id());
            rankings.add(buildRankingResult(
                    i + 1,
                    scoredRestaurant.reasonText(),
                    restaurant,
                    categoryMap,
                    region
            ));
        }
        return List.copyOf(rankings);
    }

    private RecommendResultData.Ranking buildRankingResult(
            Integer rank,
            String reasonText,
            Restaurant restaurant,
            Map<Long, Category> categoryMap,
            Region region
    ) {
        if (restaurant == null) {
            throw new CustomException(ErrorCode.RESTAURANT_NOT_FOUND);
        }

        Category category = categoryMap.get(restaurant.categoryId());
        if (category == null) {
            throw new CustomException(ErrorCode.CATEGORY_NOT_FOUND);
        }

        DistanceRange distanceRange =
                participantAnalyzer.determineMajorityDistanceRange(restaurant.location(), region);

        return RecommendResultData.Ranking.of(
                rank,
                restaurant.id(),
                restaurant.name(),
                restaurant.address(),
                restaurant.rating(),
                restaurant.imageUrl(),
                restaurant.mapUrl(),
                restaurant.representativeReview(),
                restaurant.description(),
                restaurant.region(),
                restaurant.location(),
                category.largeCategory(),
                category.mediumCategory(),
                distanceRange,
                // 추천 근거 데이터
                restaurant.reviewCount(),
                restaurant.blogReviewCount(),
                restaurant.representMenu(),
                restaurant.representMenuPrice(),
                restaurant.priceLevel(),
                restaurant.aiMateSummaryTitle(),
                restaurant.aiMateSummaryContents(),
                restaurant.teamRecommendationTitle(),
                restaurant.teamRecommendationReason(),
                // 추천 근거 텍스트
                reasonText
        );
    }
}
