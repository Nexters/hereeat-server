package com.yogieat.recommend.service;

import com.yogieat.category.domain.Category;
import com.yogieat.category.service.CategoryService;
import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import com.yogieat.gathering.domain.Gathering;
import com.yogieat.gathering.domain.result.GatheringResult;
import com.yogieat.gathering.service.GatheringEventNotifier;
import com.yogieat.gathering.service.GatheringService;
import com.yogieat.participant.domain.Participant;
import com.yogieat.participant.domain.value.DistanceRange;
import com.yogieat.participant.service.ParticipantAnalyzer;
import com.yogieat.participant.service.ParticipantService;
import com.yogieat.recommend.domain.RecommendResult;
import com.yogieat.recommend.domain.result.RecommendResultData;
import com.yogieat.recommend.domain.value.CategoryAggregation;
import com.yogieat.recommend.domain.value.RecommendStatus;
import com.yogieat.recommend.event.GatheringFullEvent;
import com.yogieat.restaurant.domain.Restaurant;
import com.yogieat.restaurant.service.RestaurantService;
import com.yogieat.util.LockManager;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendResultFacade {
    private final GatheringService gatheringService;
    private final RecommendResultService recommendResultService;
    private final RestaurantService restaurantService;
    private final CategoryService categoryService;
    private final ParticipantService participantService;
    private final ParticipantAnalyzer participantAnalyzer;
    private final LockManager lockManager;
    private final ApplicationEventPublisher eventPublisher;
    private final GatheringEventNotifier gatheringEventNotifier;
    private final RecommendValidator recommendValidator;

    @Transactional(readOnly = true)
    public RecommendResultData.Get getRecommendResults(String accessKey) {
        // 1. accessKey로 Gathering 조회
        Gathering gathering = gatheringService.getGatheringByAccessKey(accessKey);

        // 2. gatheringId로 RecommendResult 목록 조회 (rank 순서대로)
        List<RecommendResult> recommendResults = recommendResultService.findByGatheringId(gathering.id());

        // 3. 결과가 없는 경우
        if (recommendResults.isEmpty()) {
            return RecommendResultData.Get.ofEmpty();
        }

        // 4. PENDING 상태인 경우
        if (recommendResults.getFirst().status() == RecommendStatus.PENDING) {
            return RecommendResultData.Get.ofPending();
        }

        // 5. 참여자 목록 조회
        List<Participant> participants = participantService.findByGatheringId(gathering.id());

        // 6. 다수결 DistanceRange 결정
        DistanceRange majorityDistanceRange = participantAnalyzer.determineMajorityDistanceRange(participants);

        // 7. 카테고리별 선호도/불호 집계
        CategoryAggregation aggregation = participantAnalyzer.aggregateCategoryPreferences(participants);

        // 7-1. DistanceRange별 집계
        Map<String, Integer> distances = participantAnalyzer.aggregateDistanceRanges(participants);

        // 8. Restaurant 정보와 Category 정보 조회 및 캐싱
        List<Long> restaurantIds = recommendResults.stream()
                .map(RecommendResult::restaurantId)
                .toList();
        Map<Long, Restaurant> restaurantMap = restaurantService.findByIds(restaurantIds).stream()
                .collect(Collectors.toMap(Restaurant::id, Function.identity()));
        Map<Long, Category> categoryMap = categoryService.findAll().stream()
                .collect(Collectors.toMap(Category::id, Function.identity()));

        // 9. Result 생성
        List<RecommendResultData.Ranking> rankings = recommendResults.stream()
                .map(result -> buildRankingResult(result, restaurantMap, categoryMap, majorityDistanceRange))
                .toList();

        // 10. 평균 의견 일치율 계산 (소수점 둘째자리 반올림)
        double averageAgreementRate = Math.round(recommendResults.stream()
                .mapToDouble(RecommendResult::agreementRate)
                .average()
                .orElse(0.0) * 100.0) / 100.0;

        return RecommendResultData.Get.of(
                RecommendStatus.COMPLETED,
                rankings,
                aggregation.preferences(),
                aggregation.dislikes(),
                distances,
                averageAgreementRate
        );
    }

    @Transactional
    public void proceedRecommendation(String accessKey) {
        lockManager.executeWithLock(accessKey, () -> {
            // 1. Gathering 조회 (없음/삭제 시 예외 자동 발생)
            Gathering gathering = gatheringService.getGatheringByAccessKey(accessKey);

            // 2. 현재 참여자 수 조회
            long currentCount = participantService.countByGatheringId(gathering.id());

            // 3. 과반수 조건 검증 (currentCount * 2 >= peopleCount)
            recommendValidator.validateMajorityReached(currentCount, gathering.peopleCount());

            // 4. 이미 추천 진행 중 또는 완료된 경우 중복 방지
            recommendValidator.validateNotAlreadyProceeded(
                    recommendResultService.existsByGatheringId(gathering.id()));

            // 5. SSE 알림
            GatheringResult.ParticipantCount status =
                    GatheringResult.ParticipantCount.of(currentCount, gathering.peopleCount());
            gatheringEventNotifier.notifyGatheringFull(accessKey, status);

            // 6. PENDING 상태 생성 및 이벤트 발행
            recommendResultService.createPendingStatus(gathering.id());
            log.info("Publishing GatheringFullEvent by majority for gathering: {}", gathering.id());
            eventPublisher.publishEvent(new GatheringFullEvent(
                    this, gathering.id(), gathering.region(), gathering.peopleCount()
            ));
            return null;
        });
    }

    private RecommendResultData.Ranking buildRankingResult(
            RecommendResult result,
            Map<Long, Restaurant> restaurantMap,
            Map<Long, Category> categoryMap,
            DistanceRange majorityDistanceRange) {
        Restaurant restaurant = restaurantMap.get(result.restaurantId());
        if (restaurant == null) {
            throw new CustomException(ErrorCode.RESTAURANT_NOT_FOUND);
        }

        Category category = categoryMap.get(restaurant.categoryId());
        if (category == null) {
            throw new CustomException(ErrorCode.CATEGORY_NOT_FOUND);
        }

        return RecommendResultData.Ranking.of(
                result.rank(),
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
                majorityDistanceRange,
                // 추천 근거 데이터
                restaurant.reviewCount(),
                restaurant.blogReviewCount(),
                restaurant.representMenu(),
                restaurant.representMenuPrice(),
                restaurant.priceLevel(),
                restaurant.aiMateSummaryTitle(),
                restaurant.aiMateSummaryContents(),
                // 추천 근거 텍스트
                result.reasonText()
        );
    }
}
