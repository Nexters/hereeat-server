package com.yogieat.gathering.domain.command;

import com.yogieat.common.Region;
import com.yogieat.gathering.domain.value.TimeSlot;
import java.time.LocalDate;

public record GatheringCommand() {
    public record Create(
            Integer peopleCount,
            LocalDate scheduledDate,
            TimeSlot timeSlot,
            Region region
    ) {
        public static Create of(
                Integer peopleCount,
                LocalDate scheduledDate,
                TimeSlot timeSlot,
                Region region
        ) {
            return new Create(
                    peopleCount,
                    scheduledDate,
                    timeSlot,
                    region
            );
        }
    }
}
