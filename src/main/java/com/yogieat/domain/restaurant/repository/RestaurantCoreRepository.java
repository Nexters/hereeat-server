package com.yogieat.domain.restaurant.repository;

import com.yogieat.domain.common.GeoConverter;
import com.yogieat.domain.restaurant.domain.CreateRestaurant;
import com.yogieat.domain.restaurant.domain.Restaurant;
import com.yogieat.domain.restaurant.entity.RestaurantEntity;
import com.yogieat.domain.restaurant.service.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * Core implementation of RestaurantRepository
 * Bridges domain layer (Restaurant) and persistence layer (RestaurantEntity)
 */
@Repository
@RequiredArgsConstructor
public class RestaurantCoreRepository implements RestaurantRepository {

    private final RestaurantJpaRepository restaurantJpaRepository;
    private final GeoConverter geoConverter;

    @Override
    public boolean existsByExternalId(String externalId) {
        return restaurantJpaRepository.existsByExternalId(externalId);
    }

    @Override
    public boolean existsByNameAndAddress(String name, String address) {
        return restaurantJpaRepository.existsByNameAndAddress(name, address);
    }

    @Override
    public Restaurant save(CreateRestaurant createRestaurant) {
        // Convert CreateRestaurant to entity using static factory method
        RestaurantEntity entity = RestaurantEntity.from(createRestaurant, geoConverter);

        // Save and convert back to domain
        RestaurantEntity savedEntity = restaurantJpaRepository.save(entity);
        return RestaurantEntity.toDomain(savedEntity);
    }
}
