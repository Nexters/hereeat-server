package com.yogieat.controller.v1.recommend.response;

import com.yogieat.recommend.domain.result.RecommendResultData;
import com.yogieat.recommend.domain.value.RecommendStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

@Schema(description = "추천 결과 조회 응답 정보")
public record GetRecommendResultResponse(
        @Schema(description = "추천 처리 상태 (PENDING: 처리 중, COMPLETED: 완료, FAILED: 실패)",
                example = "COMPLETED")
        RecommendStatus status,
        @Schema(description = "1위 추천 결과")
        RankingRecommendResultResponse topRecommendation,
        @Schema(description = "2, 3위 추천 결과 리스트")
        List<RankingRecommendResultResponse> otherCandidates,
        @Schema(description = "카테고리별 선호도 집계", example = "{\"KOREAN\": 3, \"WESTERN\": 2}")
        Map<String, Integer> preferences,
        @Schema(description = "카테고리별 불호 집계", example = "{\"CHINESE\": 1}")
        Map<String, Integer> dislikes,
        @Schema(description = "거리 범위별 집계", example = "{\"RANGE_500M\": 2, \"RANGE_1KM\": 1, \"ANY\": 1}")
        Map<String, Integer> distances,
        @Schema(description = "의견 일치율 (%)", example = "85.5")
        Double agreementRate
) {
    public static GetRecommendResultResponse from(RecommendResultData.Get result) {
        List<RankingRecommendResultResponse> rankings = result.rankings().stream()
                .map(RankingRecommendResultResponse::from)
                .toList();

        // 1등과 나머지 후보 분리
        RankingRecommendResultResponse topRecommendation = rankings.isEmpty() ? null : rankings.getFirst();
        List<RankingRecommendResultResponse> otherCandidates = rankings.size() > 1
                ? rankings.subList(1, rankings.size())
                : List.of();

        return new GetRecommendResultResponse(
                result.status(),
                topRecommendation,
                otherCandidates,
                result.preferences(),
                result.dislikes(),
                result.distances(),
                result.averageAgreementRate()
        );
    }
}
