package com.yogieat.recommend.service;

import com.yogieat.recommend.domain.RecommendResult;
import java.time.Duration;
import java.util.List;

public interface RecommendResultRepository {
    List<RecommendResult> saveAll(List<RecommendResult> recommendResults);
    List<RecommendResult> findByGatheringId(Long gatheringId);
    boolean existsByGatheringId(Long gatheringId);
    void deleteByGatheringId(Long gatheringId);
    List<RecommendResult> findOrphanedPending(Duration threshold);
}
