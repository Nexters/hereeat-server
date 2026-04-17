package com.yogieat.datasource.db.core.region;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegionJpaRepository extends JpaRepository<RegionEntity, Long> {
    Optional<RegionEntity> findByCode(String code);

    List<RegionEntity> findAllByActiveTrueOrderBySortOrderAsc();

    @Query("select r.id from RegionEntity r where r.code = :code")
    Optional<Long> findIdByCode(@Param("code") String code);
}
