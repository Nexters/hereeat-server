package com.yogieat.controller.v1.restaurant.response;

import com.yogieat.common.Region;
import com.yogieat.restaurant.domain.Restaurant;
import com.yogieat.service.restaurant.result.RestaurantAdminListItemResult;
import java.time.LocalDateTime;

public record RestaurantListItemResponse(
        Long id,
        String name,
        Long categoryId,
        Double rating,
        String imageUrl,
        Region region,
        LocalDateTime updatedAt
) {
    public static RestaurantListItemResponse from(Restaurant restaurant) {
        return new RestaurantListItemResponse(
                restaurant.id(),
                restaurant.name(),
                restaurant.categoryId(),
                restaurant.rating(),
                restaurant.imageUrl(),
                restaurant.region(),
                restaurant.updatedAt()
        );
    }

    public static RestaurantListItemResponse from(RestaurantAdminListItemResult result) {
        return new RestaurantListItemResponse(
                result.id(),
                result.name(),
                result.categoryId(),
                result.rating(),
                result.imageUrl(),
                result.region(),
                result.updatedAt()
        );
    }
}
