package com.yogieat.restaurant.result;

import com.yogieat.common.GeoJson;
import com.yogieat.common.Region;
import com.yogieat.gathering.domain.value.TimeSlot;
import com.yogieat.restaurant.domain.Restaurant;
import java.time.LocalDateTime;
import java.util.List;

public final class RestaurantAdminResult {

    private RestaurantAdminResult() {
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
            GeoJson.Point location,
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
        public static Detail of(
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
            return new Detail(
                    id,
                    externalId,
                    categoryId,
                    name,
                    address,
                    rating,
                    imageUrl,
                    mapUrl,
                    representativeReview,
                    description,
                    region,
                    location,
                    reviewCount,
                    blogReviewCount,
                    representMenu,
                    representMenuPrice,
                    priceLevel,
                    aiMateSummaryTitle,
                    aiMateSummaryContents,
                    timeSlot,
                    createdAt,
                    updatedAt
            );
        }

        public static Detail from(Restaurant restaurant) {
            return new Detail(
                    restaurant.id(),
                    restaurant.externalId(),
                    restaurant.categoryId(),
                    restaurant.name(),
                    restaurant.address(),
                    restaurant.rating(),
                    restaurant.imageUrl(),
                    restaurant.mapUrl(),
                    restaurant.representativeReview(),
                    restaurant.description(),
                    restaurant.region(),
                    restaurant.location(),
                    restaurant.reviewCount(),
                    restaurant.blogReviewCount(),
                    restaurant.representMenu(),
                    restaurant.representMenuPrice(),
                    restaurant.priceLevel(),
                    restaurant.aiMateSummaryTitle(),
                    restaurant.aiMateSummaryContents(),
                    restaurant.timeSlot(),
                    restaurant.createdAt(),
                    restaurant.updatedAt()
            );
        }
    }
}
