package com.yogieat.datasource.db.core.restaurant;

import com.yogieat.common.Region;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestaurantJpaRepository extends JpaRepository<RestaurantEntity, Long> {
    boolean existsByExternalId(String externalId);
    boolean existsByNameAndAddress(String name, String address);
    Optional<RestaurantEntity> findByExternalId(String externalId);
    List<RestaurantEntity> findByRegion(Region region);
    List<RestaurantEntity> findByIdIn(List<Long> ids);
    long countByRegion(Region region);  // 지역별 맛집 수 조회
}
