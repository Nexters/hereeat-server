package com.yogieat.datasource.db.core.recommend;

import com.yogieat.recommend.domain.RecommendResultFailed;
import com.yogieat.recommend.service.RecommendResultFailedRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RecommendResultFailedCoreRepository implements RecommendResultFailedRepository {

    private final RecommendResultFailedJpaRepository jpaRepository;

    @Override
    public RecommendResultFailed save(RecommendResultFailed recommendResultFailed) {
        RecommendResultFailedEntity entity = RecommendResultFailedEntity.from(recommendResultFailed);
        RecommendResultFailedEntity savedEntity = jpaRepository.save(entity);
        return savedEntity.toDomain();
    }
}
