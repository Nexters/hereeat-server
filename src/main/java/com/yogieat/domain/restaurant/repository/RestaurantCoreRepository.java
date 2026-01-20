package com.yogieat.domain.restaurant.repository;

import com.yogieat.domain.common.GeoConverter;
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
    public Restaurant save(Restaurant restaurant) {
        // Convert domain to entity
        RestaurantEntity entity = RestaurantEntity.builder()
            .externalId(restaurant.externalId())
            .categoryId(restaurant.categoryId())
            .name(restaurant.name())
            .address(restaurant.address())
            .rating(restaurant.rating())
            .imageUrl(restaurant.imageUrl())
            .mapUrl(restaurant.mapUrl())
            .representativeReview(restaurant.representativeReview())
            .description(restaurant.description())
            .location(restaurant.location() != null
                ? geoConverter.geoJsonPointToJtsPoint(restaurant.location())
                : null)
            .build();

        // Save and convert back to domain
        RestaurantEntity savedEntity = restaurantJpaRepository.save(entity);
        return RestaurantEntity.toDomain(savedEntity);
    }
}
