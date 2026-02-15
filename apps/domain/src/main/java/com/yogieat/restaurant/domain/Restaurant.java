package com.yogieat.restaurant.domain;

import com.yogieat.common.GeoJson;
import com.yogieat.common.Region;
import com.yogieat.gathering.domain.value.TimeSlot;

public record Restaurant(
        Long id,
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
        String aiMateSummaryContents,  // JSON 문자열
        // 추천 시간대 (신규 필드)
        TimeSlot timeSlot
){
}
