package com.yogieat.domain.recommend.service;

import com.yogieat.domain.category.domain.Category;
import com.yogieat.domain.category.service.CategoryService;
import com.yogieat.domain.gathering.domain.Gathering;
import com.yogieat.domain.gathering.service.GatheringService;
import com.yogieat.domain.participant.domain.Participant;
import com.yogieat.domain.participant.domain.value.DistanceRange;
import com.yogieat.domain.participant.service.ParticipantAnalyzer;
import com.yogieat.domain.participant.service.ParticipantService;
import com.yogieat.domain.recommend.domain.RecommendResult;
import com.yogieat.domain.recommend.domain.result.RecommendResultResult;
import com.yogieat.domain.recommend.domain.value.CategoryAggregation;
import com.yogieat.domain.restaurant.domain.Restaurant;
import com.yogieat.domain.restaurant.service.RestaurantService;
import com.yogieat.global.error.CustomException;
import com.yogieat.global.error.ErrorCode;
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
    public RecommendResultResult.Get getRecommendResults(String accessKey) {
        // 1. accessKey로 Gathering 조회
        Gathering gathering = gatheringService.getGatheringByAccessKey(accessKey);

        // 2. gatheringId로 RecommendResult 목록 조회 (rank 순서대로)
        List<RecommendResult> recommendResults = recommendResultService.findByGatheringId(gathering.id());

        if (recommendResults.isEmpty()) {
            return RecommendResultResult.Get.of(
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
        List<RecommendResultResult.Ranking> rankings = recommendResults.stream()
                .map(result -> buildRankingResult(result, restaurantMap, categoryMap, majorityDistanceRange))
                .toList();

        // 8. 평균 의견 일치율 계산
        double averageAgreementRate = recommendResults.stream()
                .mapToDouble(RecommendResult::agreementRate)
                .average()
                .orElse(0.0);

        return RecommendResultResult.Get.of(
                rankings,
                aggregation.preferences(),
                aggregation.dislikes(),
                averageAgreementRate
        );
    }

    private RecommendResultResult.Ranking buildRankingResult(
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

        return RecommendResultResult.Ranking.of(
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
