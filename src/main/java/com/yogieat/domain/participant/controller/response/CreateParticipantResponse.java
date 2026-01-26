package com.yogieat.domain.participant.controller.response;

import com.yogieat.domain.participant.domain.result.ParticipantResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "참여자 생성 응답 정보")
public record CreateParticipantResponse (
        @Schema(description = "모임 접근키")
        String accessKey,
        @Schema(description = "참여자 ID")
        Long participantId,
        @Schema(description = "모임 ID")
        Long gatheringId
) {
    public static CreateParticipantResponse from(
            ParticipantResult.Create result
    ) {
        return new CreateParticipantResponse(
                result.accessKey(),
                result.participantId(),
                result.gatheringId()
        );
    }
}
