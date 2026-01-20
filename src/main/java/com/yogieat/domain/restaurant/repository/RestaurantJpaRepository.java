package com.yogieat.domain.restaurant.repository;

import com.yogieat.domain.restaurant.entity.RestaurantEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestaurantJpaRepository extends JpaRepository<RestaurantEntity, Long> {
    boolean existsByExternalId(String externalId);
    Optional<RestaurantEntity> findByExternalId(String externalId);
}
