package com.yogieat.recommend.event;

import com.yogieat.gathering.domain.result.GatheringResult;
import com.yogieat.gathering.service.GatheringEventNotifier;
import com.yogieat.recommend.service.RecommendationProcessor;
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

    private final RecommendationProcessor recommendationProcessor;
    private final GatheringEventNotifier gatheringEventNotifier;

    @Async
    @TransactionalEventListener
    public void handleRecommendResultCreatedEvent(RecommendResultCreatedEvent event) {
        try {
            recommendationProcessor.processRecommendation(event.getGatheringId(), event.getRegion());
            log.info("Successfully processed recommendation for gathering: {}", event.getGatheringId());

            GatheringResult.ParticipantCount status =
                    GatheringResult.ParticipantCount.of(event.getCurrentCount(), event.getPeopleCount());
            gatheringEventNotifier.notifyRecommendResultCreated(event.getAccessKey(), status);

        } catch (Exception e) {
            log.error("Failed to process recommendation for gathering: {}",
                      event.getGatheringId(), e);
        }
    }
}
