package com.yogieat.recommend.service;

import com.yogieat.recommend.domain.RecommendRerollHistory;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecommendRerollHistoryService {

    private final RecommendRerollHistoryRepository recommendRerollHistoryRepository;

    @Transactional
    public RecommendRerollHistory create(
            Long gatheringId,
            List<Long> excludedRestaurantIds,
            List<RecommendRerollHistory.Result> rerolledResults
    ) {
        return recommendRerollHistoryRepository.save(
                RecommendRerollHistory.Create.of(gatheringId, excludedRestaurantIds, rerolledResults)
        );
    }

    @Transactional(readOnly = true)
    public Optional<RecommendRerollHistory> findLatestByGatheringId(Long gatheringId) {
        return recommendRerollHistoryRepository.findLatestByGatheringId(gatheringId);
    }
}
