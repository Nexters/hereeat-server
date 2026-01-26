package com.yogieat.domain.recommend.service;

import com.yogieat.domain.recommend.domain.RecommendResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendResultService {
    private final RecommendResultRepository recommendResultRepository;

    public List<RecommendResult> findByGatheringId(Long gatheringId) {
        return recommendResultRepository.findByGatheringId(gatheringId);
    }
}
