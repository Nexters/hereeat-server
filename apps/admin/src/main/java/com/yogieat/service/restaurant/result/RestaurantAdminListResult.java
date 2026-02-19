package com.yogieat.service.restaurant.result;

import com.yogieat.service.common.PaginationResult;
import java.util.List;

public record RestaurantAdminListResult(
        List<RestaurantAdminListItemResult> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    public static RestaurantAdminListResult of(
            List<RestaurantAdminListItemResult> content,
            int page,
            int size,
            long totalElements
    ) {
        PaginationResult pagination = PaginationResult.of(page, size, totalElements);
        return new RestaurantAdminListResult(
                content,
                page,
                size,
                totalElements,
                pagination.totalPages(),
                pagination.hasNext()
        );
    }
}
