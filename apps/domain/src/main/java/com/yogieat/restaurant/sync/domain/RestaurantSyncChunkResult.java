package com.yogieat.restaurant.sync.domain;

import java.util.List;

public record RestaurantSyncChunkResult(
        int processedCount,
        int successCount,
        int failedCount,
        List<String> errorMessages
) {
    public static RestaurantSyncChunkResult of(
            int processedCount,
            int successCount,
            int failedCount,
            List<String> errorMessages
    ) {
        return new RestaurantSyncChunkResult(processedCount, successCount, failedCount, errorMessages);
    }
}
