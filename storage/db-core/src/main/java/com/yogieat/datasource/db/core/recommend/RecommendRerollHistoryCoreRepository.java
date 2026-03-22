package com.yogieat.datasource.db.core.recommend;

import com.yogieat.recommend.domain.RecommendRerollHistory;
import com.yogieat.recommend.service.RecommendRerollHistoryRepository;
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
}
