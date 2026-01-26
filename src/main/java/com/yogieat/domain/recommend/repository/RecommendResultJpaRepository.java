package com.yogieat.domain.recommend.repository;

import com.yogieat.domain.recommend.entity.RecommendResultEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendResultJpaRepository extends JpaRepository<RecommendResultEntity, Long> {
    List<RecommendResultEntity> findByGatheringIdOrderByRankAsc(Long gatheringId);
    boolean existsByGatheringId(Long gatheringId);
}
