package com.yogieat.datasource.db.core.recommend;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendRerollHistoryJpaRepository extends JpaRepository<RecommendRerollHistoryEntity, Long> {
    Optional<RecommendRerollHistoryEntity> findTopByGatheringIdOrderByIdDesc(Long gatheringId);
}
