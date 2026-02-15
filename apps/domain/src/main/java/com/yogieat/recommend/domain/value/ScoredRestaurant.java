package com.yogieat.recommend.domain.value;

import com.yogieat.restaurant.domain.Restaurant;

public record ScoredRestaurant(
        Restaurant restaurant,
        Double totalScore,
        Double agreementRate,
        String reasonText  // 추천 근거 텍스트 (신규)
) {
}
