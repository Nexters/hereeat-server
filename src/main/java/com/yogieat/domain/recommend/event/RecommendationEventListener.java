package com.yogieat.domain.recommend.event;

import com.yogieat.domain.recommend.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class RecommendationEventListener {
    private final RecommendationService recommendationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handleGatheringFullEvent(GatheringFullEvent event) {
        log.info("Received GatheringFullEvent for gathering: {}", event.getGatheringId());

        try {
            recommendationService.processRecommendation(event.getGatheringId(), event.getPlace());
            log.info("Successfully processed recommendation for gathering: {}", event.getGatheringId());

        } catch (Exception e) {
            log.error("Failed to process recommendation for gathering: {}",
                      event.getGatheringId(), e);
            // 예외는 AsyncExceptionHandler에서 처리됨
        }
    }
}
