package com.yogieat.controller.v1.restaurant.response;

import com.yogieat.service.restaurant.result.RestaurantAdminListResult;
import java.util.List;

public record RestaurantListResponse(
        List<RestaurantListItemResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    public static RestaurantListResponse from(RestaurantAdminListResult result) {
        List<RestaurantListItemResponse> content = result.content().stream()
                .map(RestaurantListItemResponse::from)
                .toList();

        return new RestaurantListResponse(
                content,
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages(),
                result.hasNext()
        );
    }

}
