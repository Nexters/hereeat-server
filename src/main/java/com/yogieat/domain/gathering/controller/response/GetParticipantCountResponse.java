package com.yogieat.domain.gathering.controller.response;

public record GetParticipantCountResponse(
        Long currentCount,
        Integer maxCount
) {
}
