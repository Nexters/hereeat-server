package com.yogieat.domain.participant.controller.response;

import com.yogieat.domain.participant.domain.result.ParticipantResult;

public record CreateParticipantResponse (
        Long participantId,
        Long gatheringId
) {
    public static CreateParticipantResponse from(
            ParticipantResult.Create result
    ) {
        return new CreateParticipantResponse(
                result.participantId(),
                result.gatheringId()
        );
    }
}
