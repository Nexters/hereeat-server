package com.yogieat.domain.participant.domain.result;

import com.yogieat.domain.gathering.domain.Gathering;
import com.yogieat.domain.participant.domain.Participant;

public record ParticipantResult(
) {
    public record Create(
            String accessKey,
            Long participantId,
            Long gatheringId
    ) {
        public static Create of(
                Gathering gathering,
                Participant participant
        ) {
            return new Create(
                    gathering.accessKey(),
                    participant.id(),
                    participant.gatheringId()
            );
        }
    }
}
