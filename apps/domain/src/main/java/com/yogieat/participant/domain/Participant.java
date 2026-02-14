package com.yogieat.participant.domain;

import com.yogieat.participant.domain.value.DistanceRange;
import com.yogieat.participant.domain.value.Role;

public record Participant(
        Long id,
        Long userId,
        Long gatheringId,
        String nickname,
        DistanceRange distanceRange,
        String preferences,
        String dislikes,
        Role role
) {
}
