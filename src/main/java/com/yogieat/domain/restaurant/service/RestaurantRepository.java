package com.yogieat.domain.restaurant.service;

import com.yogieat.domain.common.Region;
import com.yogieat.domain.restaurant.domain.CreateRestaurant;
import com.yogieat.domain.restaurant.domain.Restaurant;
import java.util.List;
import java.util.Optional;

public interface RestaurantRepository {
    boolean existsByExternalId(String externalId);
    boolean existsByNameAndAddress(String name, String address);
    Restaurant save(CreateRestaurant createRestaurant);
    List<Restaurant> findByRegion(Region region);
    Optional<Restaurant> findById(Long id);
    List<Restaurant> findByIds(List<Long> ids);
}
