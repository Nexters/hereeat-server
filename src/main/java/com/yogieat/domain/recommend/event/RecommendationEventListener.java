package com.yogieat.domain.recommend.event;

import com.yogieat.domain.recommend.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendationEventListener {
    private final RecommendationService recommendationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleGatheringFullEvent(GatheringFullEvent event) {
        try {
            recommendationService.processRecommendation(event.getGatheringId(), event.getPlace());
            log.info("Successfully processed recommendation for gathering: {}", event.getGatheringId());

        } catch (Exception e) {
            log.error("Failed to process recommendation for gathering: {}",
                      event.getGatheringId(), e);
        }
    }
}
