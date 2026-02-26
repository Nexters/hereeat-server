package com.yogieat.sse;

import com.yogieat.controller.v1.gathering.response.GetParticipantCountResponse;
import com.yogieat.gathering.domain.result.GatheringResult;
import com.yogieat.gathering.service.GatheringEventNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SseGatheringEventNotifier implements GatheringEventNotifier {

    private final SseEmitterManager sseEmitterManager;

    @Override
    public void notifyParticipantJoined(String accessKey, GatheringResult.ParticipantCount status) {
        sseEmitterManager.send(accessKey, "participant-count",
                GetParticipantCountResponse.from(status));
    }

    @Override
    public void notifyRecommendResultCreated(String accessKey, GatheringResult.ParticipantCount status) {
        sseEmitterManager.send(accessKey, "recommend-result-created",
                GetParticipantCountResponse.from(status));
        sseEmitterManager.complete(accessKey);
    }
}
