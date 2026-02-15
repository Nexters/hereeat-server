package com.yogieat.controller.v1.recommend.response;

import com.yogieat.category.domain.value.LargeCategory;
import com.yogieat.common.GeoJson;
import com.yogieat.common.Region;
import com.yogieat.participant.domain.value.DistanceRange;
import com.yogieat.recommend.domain.result.RecommendResultData;
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
        DistanceRange majorityDistanceRange,
        // 추천 근거 데이터 (신규 필드)
        @Schema(description = "카카오맵 리뷰 수", example = "120")
        Integer reviewCount,
        @Schema(description = "블로그 리뷰 수", example = "239")
        Integer blogReviewCount,
        @Schema(description = "대표 메뉴 이름", example = "양지곰탕")
        String representMenu,
        @Schema(description = "대표 메뉴 가격", example = "12000")
        Integer representMenuPrice,
        @Schema(description = "가격대", example = "₩₩")
        String priceLevel,
        @Schema(description = "AI 요약 제목", example = "맑고 깊은 국물에 담긴 정성 한 그릇")
        String aiMateSummaryTitle,
        @Schema(description = "AI 요약 내용 (JSON 배열 문자열)", example = "[\"양지곰탕 추천\", \"단체석\", \"콜키지 부과\"]")
        String aiMateSummaryContents,
        // 추천 근거 텍스트 (신규)
        @Schema(description = "추천 근거 텍스트", example = "5명 중 3명이 일식을 골라서\n400시간 숙성으로 완성한 겉바속촉 돈카츠\n를 추천해요")
        String reasonText
) {
    public static RankingRecommendResultResponse from(RecommendResultData.Ranking ranking) {
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
                ranking.majorityDistanceRange(),
                ranking.reviewCount(),
                ranking.blogReviewCount(),
                ranking.representMenu(),
                ranking.representMenuPrice(),
                ranking.priceLevel(),
                ranking.aiMateSummaryTitle(),
                ranking.aiMateSummaryContents(),
                ranking.reasonText()
        );
    }
}
