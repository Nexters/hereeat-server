package com.yogieat.recommend.service;

import com.yogieat.category.domain.Category;
import com.yogieat.category.service.CategoryService;
import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import com.yogieat.gathering.domain.Gathering;
import com.yogieat.gathering.service.GatheringService;
import com.yogieat.participant.domain.Participant;
import com.yogieat.participant.domain.value.DistanceRange;
import com.yogieat.participant.service.ParticipantAnalyzer;
import com.yogieat.participant.service.ParticipantService;
import com.yogieat.recommend.domain.RecommendResult;
import com.yogieat.recommend.domain.result.RecommendResultData;
import com.yogieat.recommend.domain.value.CategoryAggregation;
import com.yogieat.restaurant.domain.Restaurant;
import com.yogieat.restaurant.service.RestaurantService;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Transactional(readOnly = true)
    public RecommendResultData.Get getRecommendResults(String accessKey) {
        // 1. accessKey로 Gathering 조회
        Gathering gathering = gatheringService.getGatheringByAccessKey(accessKey);

        // 2. gatheringId로 RecommendResult 목록 조회 (rank 순서대로)
        List<RecommendResult> recommendResults = recommendResultService.findByGatheringId(gathering.id());

        if (recommendResults.isEmpty()) {
            return RecommendResultData.Get.of(
                    Collections.emptyList(),
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    0.0
            );
        }

        // 3. 참여자 목록 조회
        List<Participant> participants = participantService.findByGatheringId(gathering.id());

        // 4. 다수결 DistanceRange 결정
        DistanceRange majorityDistanceRange = participantAnalyzer.determineMajorityDistanceRange(participants);

        // 5. 카테고리별 선호도/불호 집계
        CategoryAggregation aggregation = participantAnalyzer.aggregateCategoryPreferences(participants);

        // 6. Restaurant 정보와 Category 정보 조회 및 캐싱
        List<Long> restaurantIds = recommendResults.stream()
                .map(RecommendResult::restaurantId)
                .toList();
        Map<Long, Restaurant> restaurantMap = restaurantService.findByIds(restaurantIds).stream()
                .collect(Collectors.toMap(Restaurant::id, Function.identity()));
        Map<Long, Category> categoryMap = categoryService.findAll().stream()
                .collect(Collectors.toMap(Category::id, Function.identity()));

        // 7. Result 생성
        List<RecommendResultData.Ranking> rankings = recommendResults.stream()
                .map(result -> buildRankingResult(result, restaurantMap, categoryMap, majorityDistanceRange))
                .toList();

        // 8. 평균 의견 일치율 계산 (소수점 둘째자리 반올림)
        double averageAgreementRate = Math.round(recommendResults.stream()
                .mapToDouble(RecommendResult::agreementRate)
                .average()
                .orElse(0.0) * 100.0) / 100.0;

        return RecommendResultData.Get.of(
                rankings,
                aggregation.preferences(),
                aggregation.dislikes(),
                averageAgreementRate
        );
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
                majorityDistanceRange
        );
    }
}
