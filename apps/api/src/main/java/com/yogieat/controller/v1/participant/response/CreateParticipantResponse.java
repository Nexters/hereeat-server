package com.yogieat.controller.v1.participant.response;

import com.yogieat.participant.domain.result.ParticipantResult;
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
