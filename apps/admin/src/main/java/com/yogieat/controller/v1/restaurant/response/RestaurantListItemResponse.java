package com.yogieat.controller.v1.restaurant.response;

import com.yogieat.category.domain.value.LargeCategory;
import com.yogieat.common.Region;
import com.yogieat.restaurant.result.RestaurantAdminListItemResult;
import java.time.LocalDateTime;

public record RestaurantListItemResponse(
        Long id,
        String name,
        Long categoryId,
        LargeCategory largeCategory,
        String mediumCategory,
        Double rating,
        String imageUrl,
        Region region,
        LocalDateTime updatedAt
) {
    public static RestaurantListItemResponse from(RestaurantAdminListItemResult result) {
        return new RestaurantListItemResponse(
                result.id(),
                result.name(),
                result.categoryId(),
                result.largeCategory(),
                result.mediumCategory(),
                result.rating(),
                result.imageUrl(),
                result.region(),
                result.updatedAt()
        );
    }
}
