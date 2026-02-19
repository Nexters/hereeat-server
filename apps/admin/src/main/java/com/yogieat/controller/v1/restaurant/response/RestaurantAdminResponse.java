package com.yogieat.controller.v1.restaurant.response;

import com.yogieat.common.GeoJson;
import com.yogieat.common.Region;
import com.yogieat.gathering.domain.value.TimeSlot;
import com.yogieat.restaurant.result.RestaurantAdminResult;
import java.time.LocalDateTime;
import java.util.List;

public final class RestaurantAdminResponse {

    private RestaurantAdminResponse() {
    }

    public record Detail(
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
            RestaurantLocationResponse location,
            Integer reviewCount,
            Integer blogReviewCount,
            String representMenu,
            Integer representMenuPrice,
            String priceLevel,
            String aiMateSummaryTitle,
            List<String> aiMateSummaryContents,
            TimeSlot timeSlot,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static Detail from(RestaurantAdminResult.Detail result) {
            return new Detail(
                    result.id(),
                    result.externalId(),
                    result.categoryId(),
                    result.name(),
                    result.address(),
                    result.rating(),
                    result.imageUrl(),
                    result.mapUrl(),
                    result.representativeReview(),
                    result.description(),
                    result.region(),
                    from(result.location()),
                    result.reviewCount(),
                    result.blogReviewCount(),
                    result.representMenu(),
                    result.representMenuPrice(),
                    result.priceLevel(),
                    result.aiMateSummaryTitle(),
                    result.aiMateSummaryContents(),
                    result.timeSlot(),
                    result.createdAt(),
                    result.updatedAt()
            );
        }

        private static RestaurantLocationResponse from(GeoJson.Point location) {
            if (location == null) {
                return null;
            }
            return new RestaurantLocationResponse(location.getCoordinates());
        }
    }

    public record RestaurantLocationResponse(List<Double> coordinates) {
    }
}
