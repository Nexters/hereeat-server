package com.yogieat.domain.recommend.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

@Schema(description = "추천 결과 조회 응답 정보")
public record GetRecommendResultResponse(
        @Schema(description = "추천 결과 랭킹 리스트 (상위 3개)")
        List<RankingRecommendResultResponse> results,
        @Schema(description = "카테고리별 선호도 집계", example = "{\"KOREAN\": 3, \"WESTERN\": 2}")
        Map<String, Integer> preferences,
        @Schema(description = "카테고리별 불호 집계", example = "{\"CHINESE\": 1}")
        Map<String, Integer> dislikes,
        @Schema(description = "의견 일치율 (%)", example = "85.5")
        Double agreementRate
) {
    public static GetRecommendResultResponse from(com.yogieat.domain.recommend.domain.result.RecommendResultResult.Get result) {
        List<RankingRecommendResultResponse> rankings = result.rankings().stream()
                .map(RankingRecommendResultResponse::from)
                .toList();

        return new GetRecommendResultResponse(
                rankings,
                result.preferences(),
                result.dislikes(),
                result.averageAgreementRate()
        );
    }
}
