package com.yogieat.domain.gathering.domain;

import com.yogieat.domain.common.Region;
import com.yogieat.domain.gathering.domain.value.TimeSlot;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record Gathering(
        Long id,
        String accessKey,
        String title,
        LocalDate scheduledDate,
        TimeSlot timeSlot,
        Region region,
        Integer peopleCount,
        LocalDateTime deletedAt
) {
    public boolean isDeleted() {
        return deletedAt != null;
    }
}
