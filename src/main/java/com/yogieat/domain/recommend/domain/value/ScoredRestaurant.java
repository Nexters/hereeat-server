package com.yogieat.domain.recommend.domain.value;

import com.yogieat.domain.restaurant.domain.Restaurant;

public record ScoredRestaurant(
        Restaurant restaurant,
        Double totalScore,
        Double agreementRate
) {
}
