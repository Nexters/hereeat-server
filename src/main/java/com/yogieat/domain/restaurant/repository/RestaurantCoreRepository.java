package com.yogieat.domain.restaurant.repository;

import com.yogieat.domain.common.GeoConverter;
import com.yogieat.domain.common.Region;
import com.yogieat.domain.restaurant.domain.CreateRestaurant;
import com.yogieat.domain.restaurant.domain.Restaurant;
import com.yogieat.domain.restaurant.entity.RestaurantEntity;
import com.yogieat.domain.restaurant.service.RestaurantRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

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

    @Override
    public List<Restaurant> findAll() {
        return restaurantJpaRepository.findAll().stream()
                .map(RestaurantEntity::toDomain)
                .toList();
    }

    @Override
    public List<Restaurant> findByRegion(Region region) {
        return restaurantJpaRepository.findByRegion(region).stream()
            .map(RestaurantEntity::toDomain)
            .toList();
    }


    @Override
    public Optional<Restaurant> findById(Long id) {
        return restaurantJpaRepository.findById(id)
                .map(RestaurantEntity::toDomain);
    }

    @Override
    public List<Restaurant> findByIds(List<Long> ids) {
        return restaurantJpaRepository.findByIdIn(ids).stream()
                .map(RestaurantEntity::toDomain)
                .toList();
    }
}
