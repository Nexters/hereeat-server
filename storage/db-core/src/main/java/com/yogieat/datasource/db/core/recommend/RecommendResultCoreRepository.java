package com.yogieat.datasource.db.core.recommend;

import com.yogieat.recommend.domain.RecommendResult;
import com.yogieat.recommend.service.RecommendResultRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RecommendResultCoreRepository implements RecommendResultRepository {
    private final RecommendResultJpaRepository recommendResultJpaRepository;

    @Override
    public List<RecommendResult> saveAll(List<RecommendResult> recommendResults) {
        List<RecommendResultEntity> entities = recommendResults.stream()
                .map(RecommendResultEntity::from)
                .toList();

        List<RecommendResultEntity> savedEntities = recommendResultJpaRepository.saveAll(entities);

        return savedEntities.stream()
                .map(RecommendResultEntity::toDomain)
                .toList();
    }

    @Override
    public List<RecommendResult> findByGatheringId(Long gatheringId) {
        List<RecommendResultEntity> entities = recommendResultJpaRepository.findByGatheringIdOrderByRankAsc(gatheringId);
        return entities.stream()
                .map(RecommendResultEntity::toDomain)
                .toList();
    }

    @Override
    public boolean existsByGatheringId(Long gatheringId) {
        return recommendResultJpaRepository.existsByGatheringId(gatheringId);
    }

    @Override
    public void deleteByGatheringId(Long gatheringId) {
        recommendResultJpaRepository.deleteByGatheringId(gatheringId);
    }

    @Override
    public List<RecommendResult> findOrphanedPending(Duration threshold) {
        LocalDateTime cutoffTime = LocalDateTime.now().minus(threshold);
        List<RecommendResultEntity> entities =
            recommendResultJpaRepository.findPendingOlderThan(cutoffTime);
        return entities.stream()
            .map(RecommendResultEntity::toDomain)
            .toList();
    }
}
