package com.yogieat.restaurant.domain;

import com.yogieat.common.GeoJson;
import com.yogieat.common.Region;
import com.yogieat.gathering.domain.value.TimeSlot;
import java.util.List;

/**
 * DTO for creating a new Restaurant
 * Separates creation concerns from the domain entity
 */
public record CreateRestaurant(
        String externalId,
        Long categoryId,
        String name,
        String address,
        Double rating,
        String imageUrl,
        String mapUrl,
        String representativeReview,
        String description,
        Region region,
        GeoJson.Point location,
        // 추천 근거 데이터 (신규 필드)
        Integer reviewCount,
        Integer blogReviewCount,
        String representMenu,
        Integer representMenuPrice,
        String priceLevel,
        String aiMateSummaryTitle,
        String aiMateSummaryContents,  // JSON 문자열로 저장
        // 추천 시간대 (신규 필드)
        TimeSlot timeSlot
) {
    public static CreateRestaurant of(
            SuggestionRestaurant suggestion,
            String placeName,
            Long categoryId,
            String externalId,
            String mapUrl,
            GeoJson.Point location,
            Double rating,
            String imageUrl,
            String representativeReview,
            Region region,
            // 추천 근거 데이터 파라미터
            Integer reviewCount,
            Integer blogReviewCount,
            String representMenu,
            Integer representMenuPrice,
            String priceLevel,
            String aiMateSummaryTitle,
            List<String> aiMateSummaryContents,
            // 추천 시간대 파라미터
            TimeSlot timeSlot
    ) {
        // List<String>을 JSON 문자열로 변환
        String aiMateSummaryContentsJson = aiMateSummaryContents != null && !aiMateSummaryContents.isEmpty()
                ? "[\"" + String.join("\", \"", aiMateSummaryContents) + "\"]"
                : null;

        return new CreateRestaurant(
                externalId,
                categoryId,
                placeName,
                suggestion.address(),
                rating,
                imageUrl,
                mapUrl,
                representativeReview != null ? representativeReview : suggestion.representativeReview(),
                suggestion.description(),
                region,
                location,
                reviewCount,
                blogReviewCount,
                representMenu,
                representMenuPrice,
                priceLevel,
                aiMateSummaryTitle,
                aiMateSummaryContentsJson,
                timeSlot
        );
    }
}
