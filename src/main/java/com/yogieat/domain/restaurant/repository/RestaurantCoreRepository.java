package com.yogieat.domain.restaurant.repository;

import com.yogieat.domain.restaurant.service.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RestaurantCoreRepository implements RestaurantRepository {
    private final RestaurantJpaRepository restaurantJpaRepository;
}
