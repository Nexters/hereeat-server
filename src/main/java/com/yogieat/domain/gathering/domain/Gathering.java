package com.yogieat.domain.gathering.domain;

import com.yogieat.domain.gathering.domain.value.Place;
import com.yogieat.domain.gathering.domain.value.TimeSlot;
import java.time.LocalDate;

public record Gathering(
        Long id,
        String accessKey,
        String title,
        LocalDate scheduledDate,
        TimeSlot timeSlot,
        Place place,
        int headCount
) {
}
