package com.yogieat.recommend.event;

import com.yogieat.recommend.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class RecommendationEventListener {

    private static final Logger log = LoggerFactory.getLogger(RecommendationEventListener.class);

    private final RecommendationService recommendationService;

    @Async
    @TransactionalEventListener
    public void handleGatheringFullEvent(GatheringFullEvent event) {
        try {
            recommendationService.processRecommendation(event.getGatheringId(), event.getRegion());
            log.info("Successfully processed recommendation for gathering: {}", event.getGatheringId());

        } catch (Exception e) {
            log.error("Failed to process recommendation for gathering: {}",
                      event.getGatheringId(), e);
        }
    }
}
