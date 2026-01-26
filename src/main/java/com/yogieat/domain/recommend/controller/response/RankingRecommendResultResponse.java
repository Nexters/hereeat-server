package com.yogieat.domain.recommend.controller.response;

import com.yogieat.domain.category.domain.value.LargeCategory;
import com.yogieat.domain.common.GeoJson;
import com.yogieat.domain.common.Region;
import com.yogieat.domain.participant.domain.value.DistanceRange;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "추천 결과 랭킹 정보")
public record RankingRecommendResultResponse(
        @Schema(description = "순위", example = "1")
        Integer rank,
        @Schema(description = "맛집 ID", example = "1")
        Long restaurantId,
        @Schema(description = "맛집 이름", example = "홍대 맛집")
        String restaurantName,
        @Schema(description = "맛집 주소", example = "서울시 마포구 ...")
        String address,
        @Schema(description = "평점", example = "4.5")
        Double rating,
        @Schema(description = "이미지 URL")
        String imageUrl,
        @Schema(description = "지도 URL")
        String mapUrl,
        @Schema(description = "대표 리뷰")
        String representativeReview,
        @Schema(description = "설명")
        String description,
        @Schema(description = "지역", example = "GANGNAM")
        Region region,
        @Schema(description = "위치 좌표")
        GeoJson.Point location,
        @Schema(description = "카테고리 대분류", example = "KOREAN")
        LargeCategory largeCategory,
        @Schema(description = "카테고리 중분류", example = "한정식")
        String mediumCategory,
        @Schema(description = "다수결 거리 범위", example = "RANGE_500M")
        DistanceRange majorityDistanceRange
) {
    public static RankingRecommendResultResponse from(com.yogieat.domain.recommend.domain.result.RecommendResultResult.Ranking ranking) {
        return new RankingRecommendResultResponse(
                ranking.rank(),
                ranking.restaurantId(),
                ranking.restaurantName(),
                ranking.address(),
                ranking.rating(),
                ranking.imageUrl(),
                ranking.mapUrl(),
                ranking.representativeReview(),
                ranking.description(),
                ranking.region(),
                ranking.location(),
                ranking.largeCategory(),
                ranking.mediumCategory(),
                ranking.majorityDistanceRange()
        );
    }
}
