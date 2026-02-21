package com.yogieat.gathering.service;

import com.yogieat.common.Region;
import com.yogieat.gathering.domain.value.TimeSlot;

public record GatheringAdminCriteria(
        List list
) {
    public record List(
            String keyword,
            Region region,
            TimeSlot timeSlot,
            boolean includeDeleted
    ) {
        public static List of(
                String keyword,
                Region region,
                TimeSlot timeSlot,
                boolean includeDeleted
        ) {
            return new List(keyword, region, timeSlot, includeDeleted);
        }
    }
}
