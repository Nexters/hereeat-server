package com.yogieat.recommend.service;

import com.yogieat.recommend.domain.RecommendResult;
import com.yogieat.recommend.domain.RecommendStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendResultService {
    private final RecommendResultRepository recommendResultRepository;

    @Transactional(readOnly = true)
    public List<RecommendResult> findByGatheringId(Long gatheringId) {
        return recommendResultRepository.findByGatheringId(gatheringId);
    }

    @Transactional
    public void createPendingStatus(Long gatheringId) {
        // 중복 방어
        if (recommendResultRepository.existsByGatheringId(gatheringId)) {
            log.warn("RecommendResult already exists for gathering: {}", gatheringId);
            return;
        }

        RecommendResult pendingResult = RecommendResult.Create.of(
                gatheringId,
                null,           // restaurantId: null for PENDING
                0.0,            // agreementRate
                RecommendStatus.PENDING,
                null,           // rank: null for PENDING
                0.0             // score
        );

        recommendResultRepository.saveAll(List.of(pendingResult));
        log.info("Created PENDING status for gathering: {}", gatheringId);
    }
}
