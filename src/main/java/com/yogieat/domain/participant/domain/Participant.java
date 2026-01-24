package com.yogieat.domain.participant.domain;

import com.yogieat.domain.participant.domain.value.DistanceRange;
import com.yogieat.domain.participant.domain.value.Role;

public record Participant(
        Long id,
        Long userId,
        Long gatheringId,
        DistanceRange distanceRange,
        String preferences,
        String dislikes,
        Role role
) {
}
