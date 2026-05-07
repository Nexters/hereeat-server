package com.yogieat.controller.v1.restaurant.response;

import com.yogieat.category.domain.value.LargeCategory;
import com.yogieat.common.Region;
import com.yogieat.restaurant.result.RestaurantDetailResult;
import java.util.List;

public final class RestaurantDetailResponse {

    private RestaurantDetailResponse() {
    }

    public record Detail(
            Long restaurantId,
            String restaurantName,
            String station,
            String address,
            Region region,
            LargeCategory largeCategory,
            Double rating,
            String imageUrl,
            String mapUrl,
            String description,
            String priceLevel,
            String representMenu,
            Integer representMenuPrice,
            String representativeReview,
            Integer reviewCount,
            String aiMateSummaryTitle,
            List<String> aiMateSummaryContents,
            String phoneNumber
    ) {
        public static Detail from(RestaurantDetailResult result) {
            return new Detail(
                    result.restaurantId(),
                    result.restaurantName(),
                    result.station(),
                    result.address(),
                    result.region(),
                    result.largeCategory(),
                    result.rating(),
                    result.imageUrl(),
                    result.mapUrl(),
                    result.description(),
                    result.priceLevel(),
                    result.representMenu(),
                    result.representMenuPrice(),
                    result.representativeReview(),
                    result.reviewCount(),
                    result.aiMateSummaryTitle(),
                    result.aiMateSummaryContents(),
                    result.phoneNumber()
            );
        }
    }
}
