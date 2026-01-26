package com.yogieat.domain.gathering.repository;

import com.yogieat.domain.gathering.entity.GatheringEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GatheringJpaRepository extends JpaRepository<GatheringEntity, Long> {
    Optional<GatheringEntity> findByAccessKey(String accessKey);
}
