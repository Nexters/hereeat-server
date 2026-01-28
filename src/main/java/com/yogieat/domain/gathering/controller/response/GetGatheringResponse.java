package com.yogieat.domain.gathering.controller.response;

import com.yogieat.domain.common.Region;
import com.yogieat.domain.gathering.domain.Gathering;
import com.yogieat.domain.gathering.domain.value.TimeSlot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "모임 단건 조회 응답 정보")
public record GetGatheringResponse(
        @Schema(description = "모임 ID", example = "1")
        Long id,
        @Schema(description = "모임 접근키", example = "c2c605069b91")
        String accessKey,
        @Schema(description = "모임 제목", example = "맛있는 음식 모임")
        String title,
        @Schema(description = "모임 예정 날짜", example = "2024-12-25")
        LocalDate scheduledDate,
        @Schema(description = "모임 시간대", example = "LUNCH")
        TimeSlot timeSlot,
        @Schema(description = "모임 지역", example = "SEOUL")
        Region region,
        @Schema(description = "모임 인원 수", example = "5")
        Integer peopleCount
) {
    public static GetGatheringResponse from(Gathering gathering) {
        return new GetGatheringResponse(
                gathering.id(),
                gathering.accessKey(),
                gathering.title(),
                gathering.scheduledDate(),
                gathering.timeSlot(),
                gathering.region(),
                gathering.peopleCount()
        );
    }
}
