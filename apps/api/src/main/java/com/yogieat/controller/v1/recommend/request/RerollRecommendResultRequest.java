package com.yogieat.controller.v1.recommend.request;

import com.yogieat.recommend.domain.command.RecommendCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

@Schema(description = "재추천 요청")
public record RerollRecommendResultRequest(
        @Schema(description = "모임 접근 키", example = "abcd1234")
        @NotBlank
        String accessKey,

        @Schema(description = "제외할 맛집 ID 목록", example = "[1, 2, 3]")
        @NotNull
        List<@NotNull @Positive Long> restaurantIds
) {
    public RerollRecommendResultRequest {
        if (accessKey != null) {
            accessKey = accessKey.strip();
        }
        if (restaurantIds != null) {
            restaurantIds = restaurantIds.stream()
                    .distinct()
                    .toList();
        }
    }

    public RecommendCommand.Reroll toCommand() {
        return RecommendCommand.Reroll.of(accessKey, restaurantIds);
    }
}
