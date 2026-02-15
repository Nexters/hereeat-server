package com.yogieat.controller.v1.restaurant.sync.response;

import com.yogieat.restaurant.sync.domain.RestaurantSyncJob;
import com.yogieat.restaurant.sync.domain.value.RestaurantSyncJobStatus;
import com.yogieat.restaurant.sync.domain.value.RestaurantSyncScope;
import java.time.LocalDateTime;

public record CreateRestaurantSyncJobResponse(
        Long jobId,
        RestaurantSyncScope scope,
        RestaurantSyncJobStatus status,
        Long targetRestaurantId,
        LocalDateTime createdAt
) {
    public static CreateRestaurantSyncJobResponse from(RestaurantSyncJob job) {
        return new CreateRestaurantSyncJobResponse(
                job.id(),
                job.scope(),
                job.status(),
                job.targetRestaurantId(),
                job.createdAt()
        );
    }
}
