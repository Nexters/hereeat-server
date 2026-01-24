package com.yogieat.domain.participant.controller.request;

import com.yogieat.domain.participant.domain.command.ParticipantCommand;
import com.yogieat.global.error.CustomException;
import com.yogieat.global.error.ErrorCode;
import java.util.List;

public record CreateParticipantRequest(
        Long gatheringId,
        Double distance,
        List<String> dislikes,
        List<String> preferences
) {
    private static final int MAX_DISLIKES_SIZE = 1;
    private static final int MAX_PREFERENCES_SIZE = 3;

    /**
     * TODO: 추후 변동사항이 있을 수도 있음
     * Compact constructor for validation
     * dislikes: 최대 1개, preferences: 최대 3개까지만 허용
     */
    public CreateParticipantRequest {
        if (dislikes != null && dislikes.size() > MAX_DISLIKES_SIZE) {
            throw new CustomException(ErrorCode.PARTICIPANT_DISLIKES_EXCEEDED);
        }
        if (preferences != null && preferences.size() > MAX_PREFERENCES_SIZE) {
            throw new CustomException(ErrorCode.PARTICIPANT_PREFERENCES_EXCEEDED);
        }
    }

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
