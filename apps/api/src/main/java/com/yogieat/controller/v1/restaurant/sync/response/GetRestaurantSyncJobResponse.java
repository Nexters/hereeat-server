package com.yogieat.controller.v1.restaurant.sync.response;

import com.yogieat.restaurant.sync.domain.RestaurantSyncJob;
import com.yogieat.restaurant.sync.domain.value.RestaurantSyncJobStatus;
import com.yogieat.restaurant.sync.domain.value.RestaurantSyncScope;
import com.yogieat.restaurant.sync.domain.value.RestaurantSyncTriggerType;
import java.time.LocalDateTime;

public record GetRestaurantSyncJobResponse(
        Long jobId,
        RestaurantSyncScope scope,
        RestaurantSyncTriggerType triggerType,
        RestaurantSyncJobStatus status,
        Long targetRestaurantId,
        Integer chunkSize,
        Integer parallelism,
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
    public static GetRestaurantSyncJobResponse from(RestaurantSyncJob job) {
        return new GetRestaurantSyncJobResponse(
                job.id(),
                job.scope(),
                job.triggerType(),
                job.status(),
                job.targetRestaurantId(),
                job.chunkSize(),
                job.parallelism(),
                job.totalCount(),
                job.processedCount(),
                job.successCount(),
                job.failedCount(),
                job.errorSummary(),
                job.startedAt(),
                job.finishedAt(),
                job.createdAt(),
                job.updatedAt()
        );
    }
}
