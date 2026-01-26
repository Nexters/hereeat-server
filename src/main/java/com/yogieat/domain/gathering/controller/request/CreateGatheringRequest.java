package com.yogieat.domain.gathering.controller.request;

import com.yogieat.domain.common.Region;
import com.yogieat.domain.gathering.domain.value.TimeSlot;
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
}
