package com.yogieat.domain.restaurant.service;

import com.yogieat.domain.common.Region;
import com.yogieat.domain.restaurant.domain.CreateRestaurant;
import com.yogieat.domain.restaurant.domain.Restaurant;
import java.util.List;

public interface RestaurantRepository {
    boolean existsByExternalId(String externalId);
    boolean existsByNameAndAddress(String name, String address);
    Restaurant save(CreateRestaurant createRestaurant);
    List<Restaurant> findByRegion(Region region);
}
