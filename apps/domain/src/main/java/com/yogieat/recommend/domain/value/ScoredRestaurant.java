package com.yogieat.recommend.domain.value;

import com.yogieat.restaurant.domain.Restaurant;

public record ScoredRestaurant(
        Restaurant restaurant,
        Double totalScore,
        Double agreementRate
) {
}
