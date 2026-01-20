package com.yogieat.domain.restaurant.service;

import com.yogieat.domain.restaurant.domain.Restaurant;

public interface RestaurantRepository {
    boolean existsByExternalId(String externalId);
    Restaurant save(Restaurant restaurant);
}
