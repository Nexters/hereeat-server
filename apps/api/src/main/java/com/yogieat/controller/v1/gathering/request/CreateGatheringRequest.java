package com.yogieat.controller.v1.gathering.request;

import com.yogieat.common.Region;
import com.yogieat.gathering.domain.command.GatheringCommand;
import com.yogieat.gathering.domain.value.TimeSlot;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Schema(description = "모임 생성 요청 정보")
public record CreateGatheringRequest(
        @Schema(description = "모임 인원수", example = "4")
        @NotNull
        @Min(value = 1)
        @Max(value = 10)
        Integer peopleCount,

        @Schema(description = "모임 날짜", example = "2026-01-27")
        @NotNull
        LocalDate scheduledDate,

        @Schema(description = "모임 시간대", example = "LUNCH")
        @NotNull
        TimeSlot timeSlot,

        @Schema(description = "모임 장소", example = "GANGNAM")
        @NotNull
        Region region
) {
    public static CreateGatheringRequest of(
            Integer peopleCount,
            LocalDate scheduledDate,
            TimeSlot timeSlot,
            Region region
    ) {
        return new CreateGatheringRequest(
                peopleCount,
                scheduledDate,
                timeSlot,
                region
        );
    }

    public GatheringCommand.Create toCommand() {
        return GatheringCommand.Create.of(
                peopleCount,
                scheduledDate,
                timeSlot,
                region
        );
    }
}
