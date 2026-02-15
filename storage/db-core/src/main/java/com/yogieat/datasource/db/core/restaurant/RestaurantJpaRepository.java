package com.yogieat.datasource.db.core.restaurant;

import com.yogieat.common.Region;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RestaurantJpaRepository extends JpaRepository<RestaurantEntity, Long> {
    boolean existsByExternalIdAndDeletedAtIsNull(String externalId);
    boolean existsByNameAndAddressAndDeletedAtIsNull(String name, String address);
    Optional<RestaurantEntity> findByExternalIdAndDeletedAtIsNull(String externalId);
    Optional<RestaurantEntity> findByIdAndDeletedAtIsNull(Long id);
    List<RestaurantEntity> findByRegionAndDeletedAtIsNull(Region region);
    List<RestaurantEntity> findByIdInAndDeletedAtIsNull(List<Long> ids);
    List<RestaurantEntity> findAllByDeletedAtIsNull();
    long countByRegionAndDeletedAtIsNull(Region region);  // 지역별 맛집 수 조회

    @Query(
        value = """
            select id
            from t_restaurant
            where deleted_at is null
              and (:lastId is null or id > :lastId)
            order by id asc
            limit :limit
            """,
        nativeQuery = true
    )
    List<Long> findActiveIdsAfter(@Param("lastId") Long lastId, @Param("limit") int limit);

    @Query("""
        select count(r.id)
        from RestaurantEntity r
        where r.deletedAt is null
        """)
    long countActiveRestaurants();
}
