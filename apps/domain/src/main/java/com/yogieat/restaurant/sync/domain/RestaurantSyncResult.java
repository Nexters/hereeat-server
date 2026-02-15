package com.yogieat.restaurant.sync.domain;

public record RestaurantSyncResult(
        Long restaurantId,
        boolean success,
        String message
) {
    public static RestaurantSyncResult success(Long restaurantId) {
        return new RestaurantSyncResult(restaurantId, true, null);
    }

    public static RestaurantSyncResult failed(Long restaurantId, String message) {
        return new RestaurantSyncResult(restaurantId, false, message);
    }
}
