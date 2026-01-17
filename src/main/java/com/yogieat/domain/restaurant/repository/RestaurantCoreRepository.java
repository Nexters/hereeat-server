package com.yogieat.domain.restaurant.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RestaurantCoreRepository implements RestaurantRepository {
    private final RestaurantJpaRepository restaurantJpaRepository;
}
