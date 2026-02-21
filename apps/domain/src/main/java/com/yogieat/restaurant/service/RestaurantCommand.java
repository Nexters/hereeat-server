package com.yogieat.restaurant.service;

import com.yogieat.common.GeoJson;
import com.yogieat.common.Region;
import com.yogieat.gathering.domain.value.TimeSlot;
import java.util.List;

public final class RestaurantCommand {

    private RestaurantCommand() {
    }

    public record Create(
            String externalId,
            Long categoryId,
            Region region,
            String description
    ) {
    }

    public record Patch(
            String externalId,
            String name,
            String address,
            Long categoryId,
            Region region,
            Double rating,
            String imageUrl,
            String mapUrl,
            String representativeReview,
            String description,
            GeoJson.Point location,
            Integer reviewCount,
            Integer blogReviewCount,
            String representMenu,
            Integer representMenuPrice,
            String priceLevel,
            String aiMateSummaryTitle,
            List<String> aiMateSummaryContents,
            TimeSlot timeSlot
    ) {
        public static Patch empty() {
            return new Patch(
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }
    }
}
