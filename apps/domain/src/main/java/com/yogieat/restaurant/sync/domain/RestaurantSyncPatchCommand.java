package com.yogieat.restaurant.sync.domain;

import com.yogieat.gathering.domain.value.TimeSlot;

public record RestaurantSyncPatchCommand(
        Long restaurantId,
        String externalId,
        String name,
        String mapUrl,
        Double longitude,
        Double latitude,
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
        TimeSlot timeSlot,
        Long categoryId,
        String offDays
) {
    public static RestaurantSyncPatchCommand of(Long restaurantId, RestaurantSyncPatch patch) {
        Double longitude = null;
        Double latitude = null;

        if (patch.location() != null
                && patch.location().getCoordinates() != null
                && patch.location().getCoordinates().size() >= 2) {
            longitude = patch.location().getCoordinates().getFirst();
            latitude = patch.location().getCoordinates().get(1);
        }

        return new RestaurantSyncPatchCommand(
                restaurantId,
                patch.externalId(),
                patch.name(),
                patch.mapUrl(),
                longitude,
                latitude,
                patch.rating(),
                patch.imageUrl(),
                patch.representativeReview(),
                patch.reviewCount(),
                patch.blogReviewCount(),
                patch.representMenu(),
                patch.representMenuPrice(),
                patch.priceLevel(),
                patch.aiMateSummaryTitle(),
                patch.aiMateSummaryContents(),
                patch.timeSlot(),
                patch.categoryId(),
                patch.offDays()
        );
    }
}
