package com.yogieat.controller.v1.recommend.response;

import com.yogieat.recommend.domain.result.RecommendResultData;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "재추천 결과 응답")
public record RerollRecommendResultResponse(
        @Schema(description = "재추천 맛집 리스트")
        List<RankingRecommendResultResponse> list
) {
    public static RerollRecommendResultResponse from(RecommendResultData.Reroll result) {
        return new RerollRecommendResultResponse(
                result.list().stream()
                        .map(RankingRecommendResultResponse::from)
                        .toList()
        );
    }
}
