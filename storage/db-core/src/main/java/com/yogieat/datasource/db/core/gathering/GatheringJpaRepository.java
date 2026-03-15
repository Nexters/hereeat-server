package com.yogieat.datasource.db.core.gathering;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GatheringJpaRepository extends JpaRepository<GatheringEntity, Long> {
    Optional<GatheringEntity> findByAccessKey(String accessKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GatheringEntity g where g.accessKey = :accessKey")
    Optional<GatheringEntity> findByAccessKeyForUpdate(@Param("accessKey") String accessKey);
}
