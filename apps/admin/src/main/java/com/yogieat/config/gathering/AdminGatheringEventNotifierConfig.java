package com.yogieat.config.gathering;

import com.yogieat.gathering.domain.result.GatheringResult;
import com.yogieat.gathering.service.GatheringEventNotifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AdminGatheringEventNotifierConfig {

    @Bean
    @ConditionalOnMissingBean(GatheringEventNotifier.class)
    public GatheringEventNotifier gatheringEventNotifier() {
        return new GatheringEventNotifier() {
            @Override
            public void notifyParticipantJoined(String accessKey, GatheringResult.ParticipantCount status) {
                // Admin app does not publish SSE events.
            }

            @Override
            public void notifyRecommendResultCreated(String accessKey, GatheringResult.ParticipantCount status) {
                // Admin app does not publish SSE events.
            }
        };
    }
}
