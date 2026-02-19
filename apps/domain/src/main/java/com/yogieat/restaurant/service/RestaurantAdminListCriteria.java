package com.yogieat.restaurant.service;

import com.yogieat.category.domain.value.LargeCategory;
import com.yogieat.common.Region;

public record RestaurantAdminListCriteria(
        String keyword,
        Region region,
        LargeCategory largeCategory,
        Long categoryId
) {
    public static RestaurantAdminListCriteria of(
            String keyword,
            Region region,
            LargeCategory largeCategory,
            Long categoryId
    ) {
        return new RestaurantAdminListCriteria(keyword, region, largeCategory, categoryId);
    }
}
