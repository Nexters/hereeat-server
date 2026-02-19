package com.yogieat.gathering.result;

import com.yogieat.gathering.domain.Gathering;

public record GatheringAdminItemResult(
        Gathering gathering,
        long participantCount
) {
}
