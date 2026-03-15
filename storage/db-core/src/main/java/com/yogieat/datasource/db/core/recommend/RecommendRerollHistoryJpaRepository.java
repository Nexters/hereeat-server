package com.yogieat.datasource.db.core.recommend;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendRerollHistoryJpaRepository extends JpaRepository<RecommendRerollHistoryEntity, Long> {
    long countByGatheringId(Long gatheringId);
    java.util.Optional<RecommendRerollHistoryEntity> findFirstByGatheringIdOrderByCreatedAtDesc(Long gatheringId);
}
