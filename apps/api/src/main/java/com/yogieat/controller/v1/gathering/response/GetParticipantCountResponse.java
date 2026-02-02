package com.yogieat.controller.v1.gathering.response;

import com.yogieat.gathering.domain.result.GatheringResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record GetParticipantCountResponse(
        @Schema(description = "현재 참여자 수", example = "2")
        Long currentCount,
        @Schema(description = "총 참여자 수", example = "4")
        Integer maxCount
) {
    public static GetParticipantCountResponse from(
            GatheringResult.ParticipantCount result
    ) {
        return new GetParticipantCountResponse(
                result.currentCount(),
                result.maxCount()
        );
    }
}
