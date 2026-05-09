package com.yogieat.restaurant.result;

import com.yogieat.category.domain.value.LargeCategory;
import com.yogieat.common.Region;
import com.yogieat.restaurant.domain.Restaurant;
import java.time.LocalDateTime;

public record RestaurantAdminListItemResult(
        Long id,
        String name,
        Long categoryId,
        LargeCategory largeCategory,
        String mediumCategory,
        Double rating,
        String imageUrl,
        Region region,
        LocalDateTime updatedAt,
        Boolean isDisplay
) {
    public static RestaurantAdminListItemResult from(Restaurant restaurant) {
        return new RestaurantAdminListItemResult(
                restaurant.id(),
                restaurant.name(),
                restaurant.categoryId(),
                null,
                null,
                restaurant.rating(),
                restaurant.imageUrl(),
                restaurant.region(),
                restaurant.updatedAt(),
                restaurant.isDisplay()
        );
    }
}
