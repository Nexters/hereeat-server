package com.yogieat.restaurant.result;

import com.yogieat.category.domain.value.LargeCategory;
import com.yogieat.common.Region;
import java.util.List;

public record RestaurantDetailResult(
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
    public static RestaurantDetailResult of(
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
        return new RestaurantDetailResult(
                restaurantId,
                restaurantName,
                station,
                address,
                region,
                largeCategory,
                rating,
                imageUrl,
                mapUrl,
                description,
                priceLevel,
                representMenu,
                representMenuPrice,
                representativeReview,
                reviewCount,
                aiMateSummaryTitle,
                aiMateSummaryContents,
                phoneNumber
        );
    }
}
