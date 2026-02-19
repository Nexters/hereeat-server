package com.yogieat.participant.domain;

import com.yogieat.participant.domain.value.DistanceRange;
import com.yogieat.participant.domain.value.Role;
import java.time.LocalDateTime;

public record Participant(
        Long id,
        Long userId,
        Long gatheringId,
        String nickname,
        DistanceRange distanceRange,
        String preferences,
        String dislikes,
        Role role,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
