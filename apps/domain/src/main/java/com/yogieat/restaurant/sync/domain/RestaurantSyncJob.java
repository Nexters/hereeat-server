package com.yogieat.restaurant.sync.domain;

import com.yogieat.restaurant.sync.domain.value.RestaurantSyncJobStatus;
import com.yogieat.restaurant.sync.domain.value.RestaurantSyncScope;
import com.yogieat.restaurant.sync.domain.value.RestaurantSyncTriggerType;
import java.time.LocalDateTime;

public record RestaurantSyncJob(
        Long id,
        RestaurantSyncScope scope,
        RestaurantSyncTriggerType triggerType,
        Long targetRestaurantId,
        RestaurantSyncJobStatus status,
        Integer chunkSize,
        Integer parallelism,
        Long lastProcessedRestaurantId,
        Long totalCount,
        Long processedCount,
        Long successCount,
        Long failedCount,
        String errorSummary,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static RestaurantSyncJob create(
            RestaurantSyncScope scope,
            RestaurantSyncTriggerType triggerType,
            Long targetRestaurantId,
            int chunkSize,
            int parallelism
    ) {
        return new RestaurantSyncJob(
                null,
                scope,
                triggerType,
                targetRestaurantId,
                RestaurantSyncJobStatus.PENDING,
                chunkSize,
                parallelism,
                null,
                0L,
                0L,
                0L,
                0L,
                null,
                null,
                null,
                null,
                null
        );
    }
}
