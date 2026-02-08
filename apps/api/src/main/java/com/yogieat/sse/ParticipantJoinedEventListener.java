package com.yogieat.sse;

import com.yogieat.controller.v1.gathering.response.GetParticipantCountResponse;
import com.yogieat.gathering.domain.result.GatheringResult;
import com.yogieat.gathering.event.ParticipantJoinedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ParticipantJoinedEventListener {

    private final SseEmitterManager sseEmitterManager;

    @EventListener
    public void handleParticipantJoined(ParticipantJoinedEvent event) {
        log.info("Sending participant SSE event - accessKey: {}, count: {}/{}",
                event.getAccessKey(), event.getCurrentCount(), event.getMaxCount());

        GatheringResult.ParticipantCount status = new GatheringResult.ParticipantCount(
                event.getCurrentCount(),
                event.getMaxCount()
        );

        sseEmitterManager.send(
                event.getAccessKey(),
                "participant-count",
                GetParticipantCountResponse.from(status)
        );

        // 인원 충족 시 SSE 연결 종료
        if (event.getCurrentCount().equals(event.getMaxCount().longValue())) {
            sseEmitterManager.send(
                    event.getAccessKey(),
                    "gathering-full",
                    GetParticipantCountResponse.from(status)
            );
            sseEmitterManager.complete(event.getAccessKey());
        }
    }
}
