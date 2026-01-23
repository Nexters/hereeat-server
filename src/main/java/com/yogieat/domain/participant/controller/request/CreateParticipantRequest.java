package com.yogieat.domain.participant.controller.request;

import com.yogieat.domain.participant.domain.command.ParticipantCommand;
import java.util.List;

public record CreateParticipantRequest(
        Long gatheringId,
        Double distance,
        List<String> dislikes,
        List<String> preferences
) {
    public static CreateParticipantRequest of(
            Long gatheringId,
            Double distance,
            List<String> dislikes,
            List<String> preferences
    ) {
        return new CreateParticipantRequest(
                gatheringId,
                distance,
                dislikes,
                preferences
        );
    }

    public ParticipantCommand.Create toCommand() {
        return ParticipantCommand.Create.of(
                gatheringId,
                distance,
                dislikes,
                preferences
        );
    }
}
