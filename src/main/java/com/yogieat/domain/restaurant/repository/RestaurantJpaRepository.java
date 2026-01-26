package com.yogieat.domain.restaurant.repository;

import com.yogieat.domain.common.Region;
import com.yogieat.domain.restaurant.entity.RestaurantEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestaurantJpaRepository extends JpaRepository<RestaurantEntity, Long> {
    boolean existsByExternalId(String externalId);
    boolean existsByNameAndAddress(String name, String address);
    Optional<RestaurantEntity> findByExternalId(String externalId);
    List<RestaurantEntity> findByRegion(Region region);
}
