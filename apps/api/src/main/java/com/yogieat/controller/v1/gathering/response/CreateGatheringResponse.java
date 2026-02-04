package com.yogieat.controller.v1.gathering.response;

import com.yogieat.gathering.domain.result.GatheringResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "모임 생성 응답 정보")
public record CreateGatheringResponse(
        @Schema(description = "모임 접근키", example = "c2c605069b91")
        String accessKey
) {
    public static CreateGatheringResponse from(
            GatheringResult.Create result
    ) {
        return new CreateGatheringResponse(
                result.accessKey()
        );
    }
}
