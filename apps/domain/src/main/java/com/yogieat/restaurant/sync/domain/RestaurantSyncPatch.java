package com.yogieat.restaurant.sync.domain;

import com.yogieat.common.GeoJson;
import com.yogieat.gathering.domain.value.TimeSlot;

public record RestaurantSyncPatch(
        String externalId,
        String name,
        String mapUrl,
        GeoJson.Point location,
        Double rating,
        String imageUrl,
        String representativeReview,
        Integer reviewCount,
        Integer blogReviewCount,
        String representMenu,
        Integer representMenuPrice,
        String priceLevel,
        String aiMateSummaryTitle,
        String aiMateSummaryContents,
        String station,
        TimeSlot timeSlot,
        Long categoryId,
        String offDays
) {
}
