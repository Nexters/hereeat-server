package com.yogieat.recommend.service;

import com.yogieat.recommend.domain.RecommendRerollHistory;
import java.util.Optional;

public interface RecommendRerollHistoryRepository {
    RecommendRerollHistory save(RecommendRerollHistory recommendRerollHistory);
    long countByGatheringId(Long gatheringId);
    Optional<RecommendRerollHistory> findLatestByGatheringId(Long gatheringId);
}
