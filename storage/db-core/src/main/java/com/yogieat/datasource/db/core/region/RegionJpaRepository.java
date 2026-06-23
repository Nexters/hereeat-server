package com.yogieat.datasource.db.core.region;

import com.yogieat.region.domain.RegionStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegionJpaRepository extends JpaRepository<RegionEntity, Long> {
    Optional<RegionEntity> findByIdAndDeletedAtIsNull(Long id);
    Optional<RegionEntity> findByCodeAndDeletedAtIsNull(String code);
    Optional<RegionEntity> findByDisplayNameAndStatusAndDeletedAtIsNull(String displayName, RegionStatus status);
    List<RegionEntity> findByIdInAndDeletedAtIsNull(List<Long> ids);

    List<RegionEntity> findAllByDeletedAtIsNullOrderBySortOrderAsc();
    List<RegionEntity> findAllByStatusAndDeletedAtIsNullOrderBySortOrderAsc(RegionStatus status);
    boolean existsByCode(String code);
    boolean existsByDisplayNameAndDeletedAtIsNull(String displayName);

    @Query("select r.id from RegionEntity r where r.code = :code and r.deletedAt is null")
    Optional<Long> findIdByCode(@Param("code") String code);

    @Query("select coalesce(max(r.sortOrder), -1) + 1 from RegionEntity r where r.deletedAt is null")
    Integer findNextSortOrder();
}
