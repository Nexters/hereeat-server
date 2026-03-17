package com.yogieat.datasource.db.core.recommend;

import com.yogieat.recommend.domain.RecommendRerollHistory;
import com.yogieat.recommend.service.RecommendRerollHistoryRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RecommendRerollHistoryCoreRepository implements RecommendRerollHistoryRepository {

    private final RecommendRerollHistoryJpaRepository recommendRerollHistoryJpaRepository;

    @Override
    public RecommendRerollHistory save(RecommendRerollHistory recommendRerollHistory) {
        RecommendRerollHistoryEntity savedEntity =
                recommendRerollHistoryJpaRepository.save(RecommendRerollHistoryEntity.from(recommendRerollHistory));
        return savedEntity.toDomain();
    }

    @Override
    public long countByGatheringId(Long gatheringId) {
        return recommendRerollHistoryJpaRepository.countByGatheringId(gatheringId);
    }

    @Override
    public Optional<RecommendRerollHistory> findLatestByGatheringId(Long gatheringId) {
        return recommendRerollHistoryJpaRepository.findFirstByGatheringIdOrderByCreatedAtDesc(gatheringId)
                .map(RecommendRerollHistoryEntity::toDomain);
    }
}
