package com.yogieat.gathering.domain;

import com.yogieat.common.Region;
import com.yogieat.gathering.domain.value.TimeSlot;
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
        LocalDateTime deletedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public boolean isDeleted() {
        return deletedAt != null;
    }
}
