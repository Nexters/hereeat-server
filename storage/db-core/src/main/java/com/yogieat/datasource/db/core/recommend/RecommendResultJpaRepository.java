package com.yogieat.datasource.db.core.recommend;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecommendResultJpaRepository extends JpaRepository<RecommendResultEntity, Long> {
    List<RecommendResultEntity> findByGatheringIdOrderByRankAsc(Long gatheringId);
    boolean existsByGatheringId(Long gatheringId);
    void deleteByGatheringId(Long gatheringId);

    @Query("""
        SELECT r FROM RecommendResultEntity r
        WHERE r.status = 'PENDING'
          AND r.createdAt < :cutoffTime
    """)
    List<RecommendResultEntity> findPendingOlderThan(
        @Param("cutoffTime") LocalDateTime cutoffTime
    );
}
