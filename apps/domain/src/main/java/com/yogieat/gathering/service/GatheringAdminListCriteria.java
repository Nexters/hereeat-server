package com.yogieat.gathering.service;

import com.yogieat.common.Region;
import com.yogieat.gathering.domain.value.TimeSlot;

public record GatheringAdminListCriteria(
        String keyword,
        Region region,
        TimeSlot timeSlot,
        boolean includeDeleted
) {
    public static GatheringAdminListCriteria of(
            String keyword,
            Region region,
            TimeSlot timeSlot,
            boolean includeDeleted
    ) {
        return new GatheringAdminListCriteria(keyword, region, timeSlot, includeDeleted);
    }
}
