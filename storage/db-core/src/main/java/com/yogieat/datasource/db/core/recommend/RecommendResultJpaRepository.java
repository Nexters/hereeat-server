package com.yogieat.datasource.db.core.recommend;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendResultJpaRepository extends JpaRepository<RecommendResultEntity, Long> {
    List<RecommendResultEntity> findByGatheringIdOrderByRankAsc(Long gatheringId);
    boolean existsByGatheringId(Long gatheringId);
    void deleteByGatheringId(Long gatheringId);
}
