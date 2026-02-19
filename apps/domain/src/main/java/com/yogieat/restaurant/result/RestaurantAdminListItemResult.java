package com.yogieat.restaurant.result;

import com.yogieat.common.Region;
import com.yogieat.restaurant.domain.Restaurant;
import java.time.LocalDateTime;

public record RestaurantAdminListItemResult(
        Long id,
        String name,
        Long categoryId,
        Double rating,
        String imageUrl,
        Region region,
        LocalDateTime updatedAt
) {
    public static RestaurantAdminListItemResult from(Restaurant restaurant) {
        return new RestaurantAdminListItemResult(
                restaurant.id(),
                restaurant.name(),
                restaurant.categoryId(),
                restaurant.rating(),
                restaurant.imageUrl(),
                restaurant.region(),
                restaurant.updatedAt()
        );
    }
}
