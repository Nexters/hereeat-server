package com.yogieat.recommend.domain.value;

/**
 * 카테고리명과 점수 계산된 레스토랑을 함께 담는 값 객체입니다.
 */
public record CategoryScoredRestaurant(
        String categoryName,
        ScoredRestaurant scoredRestaurant
) {
}
