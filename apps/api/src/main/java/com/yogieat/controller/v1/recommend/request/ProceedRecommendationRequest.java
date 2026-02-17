package com.yogieat.controller.v1.recommend.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "추천 진행 요청")
public record ProceedRecommendationRequest(
        @Schema(description = "모임 접근 키", example = "abcd1234")
        String accessKey
) {
}
