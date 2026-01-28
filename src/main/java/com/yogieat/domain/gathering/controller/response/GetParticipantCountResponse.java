package com.yogieat.domain.gathering.controller.response;

public record GetParticipantCountResponse(
        Long currentPeopleCount,
        Integer maxPeopleCount
) {
}
