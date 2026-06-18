package com.yogieat.controller.v2.recommend.response;

import com.yogieat.common.Region;
import com.yogieat.controller.v1.recommend.response.RankingRecommendResultResponse;
import com.yogieat.gathering.domain.value.TimeSlot;
import com.yogieat.recommend.domain.result.RecommendResultData;
import com.yogieat.recommend.domain.value.RecommendStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Schema(description = "추천 결과 조회 응답 정보")
public record GetRecommendResultResponse(
        @Schema(description = "추천 처리 상태 (PENDING: 처리 중, COMPLETED: 완료, FAILED: 실패)", example = "COMPLETED")
        RecommendStatus status,
        @Schema(description = "추천 결과 맛집 리스트")
        List<RankingRecommendResultResponse> recommendResults,
        @Schema(description = "모임 정보")
        GatheringInfo gathering,
        @Schema(description = "카테고리별 선호도 집계", example = "{\"KOREAN\": 3, \"WESTERN\": 2}")
        Map<String, Integer> preferences,
        @Schema(description = "카테고리별 불호 집계", example = "{\"CHINESE\": 1}")
        Map<String, Integer> dislikes,
        @Schema(description = "거리 범위별 집계", example = "{\"RANGE_500M\": 2, \"RANGE_1KM\": 1, \"ANY\": 1}")
        Map<String, Integer> distances,
        @Schema(description = "의견 일치율 (%)", example = "85.5")
        Double agreementRate
) {
    public record GatheringInfo(
            LocalDate scheduledDate,
            TimeSlot timeSlot,
            Region region,
            Integer peopleCount
    ) {
        public static GatheringInfo from(RecommendResultData.GatheringInfo info) {
            return new GetRecommendResultResponse.GatheringInfo(
                    info.scheduledDate(),
                    info.timeSlot(),
                    info.region(),
                    info.peopleCount()
            );
        }
    }

    public static GetRecommendResultResponse from(RecommendResultData.Get result) {
        List<RankingRecommendResultResponse> recommendResults = result.rankings().stream()
                .map(RankingRecommendResultResponse::from)
                .toList();

        return new GetRecommendResultResponse(
                result.status(),
                recommendResults,
                result.gathering() != null ? GatheringInfo.from(result.gathering()) : null,
                result.preferences(),
                result.dislikes(),
                result.distances(),
                result.averageAgreementRate()
        );
    }
}
