package com.yogieat.recommend.service.strategy;

import com.yogieat.recommend.domain.value.CategoryScoredRestaurant;
import com.yogieat.recommend.domain.value.ScoredRestaurant;
import java.util.List;
import java.util.Map;

/**
 * 추천 후보군에서 최종 Top-K를 선정하는 전략 인터페이스입니다.
 */
public interface RecommendationSelectionStrategy {

    List<ScoredRestaurant> selectTopRestaurants(
            List<CategoryScoredRestaurant> scoredByCategory,
            Map<String, Integer> preferenceVotes,
            int topKSize,
            int candidatePoolSize
    );
}
