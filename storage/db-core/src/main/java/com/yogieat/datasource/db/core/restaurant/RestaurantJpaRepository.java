package com.yogieat.datasource.db.core.restaurant;

import com.yogieat.common.Region;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestaurantJpaRepository extends JpaRepository<RestaurantEntity, Long> {
    boolean existsByExternalIdAndDeletedAtIsNull(String externalId);
    boolean existsByNameAndAddressAndDeletedAtIsNull(String name, String address);
    Optional<RestaurantEntity> findByExternalIdAndDeletedAtIsNull(String externalId);
    Optional<RestaurantEntity> findByIdAndDeletedAtIsNull(Long id);
    List<RestaurantEntity> findByRegionAndDeletedAtIsNull(Region region);
    List<RestaurantEntity> findByIdInAndDeletedAtIsNull(List<Long> ids);
    List<RestaurantEntity> findAllByDeletedAtIsNull();
    long countByRegionAndDeletedAtIsNull(Region region);  // 지역별 맛집 수 조회
    long countByDeletedAtIsNull();
}
