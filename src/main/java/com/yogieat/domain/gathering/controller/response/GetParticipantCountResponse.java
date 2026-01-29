package com.yogieat.domain.gathering.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record GetParticipantCountResponse(
        @Schema(description = "현재 참여자 수", example = "2")
        Long currentCount,
        @Schema(description = "총 참여자 수", example = "4")
        Integer maxCount
) {
}
