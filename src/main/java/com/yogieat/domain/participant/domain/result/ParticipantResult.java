package com.yogieat.domain.participant.domain.result;

import com.yogieat.domain.participant.domain.Participant;

public record ParticipantResult(
) {
    public record Create(
            Long participantId,
            Long gatheringId
    ) {
        public static Create of(
                Participant participant
        ) {
            return new Create(
                    participant.id(),
                    participant.gatheringId()
            );
        }
    }
}
