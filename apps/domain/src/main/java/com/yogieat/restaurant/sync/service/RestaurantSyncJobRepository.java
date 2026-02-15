package com.yogieat.restaurant.sync.service;

import com.yogieat.restaurant.sync.domain.RestaurantSyncJob;
import com.yogieat.restaurant.sync.domain.value.RestaurantSyncJobStatus;
import com.yogieat.restaurant.sync.domain.value.RestaurantSyncScope;
import java.util.Optional;

public interface RestaurantSyncJobRepository {
    RestaurantSyncJob save(RestaurantSyncJob syncJob);

    Optional<RestaurantSyncJob> findById(Long id);

    Optional<RestaurantSyncJob> findTopByStatusOrderByCreatedAtAsc(RestaurantSyncJobStatus status);

    boolean existsByScopeAndStatus(RestaurantSyncScope scope, RestaurantSyncJobStatus status);

    boolean existsByTargetRestaurantIdAndStatus(Long targetRestaurantId, RestaurantSyncJobStatus status);

    void markRunning(Long jobId);

    void updateProgress(
            Long jobId,
            Long lastProcessedRestaurantId,
            long processedIncrement,
            long successIncrement,
            long failedIncrement
    );

    void markSuccess(Long jobId);

    void markPartialFailed(Long jobId, String errorSummary);

    void markFailed(Long jobId, String errorSummary);

    void initializeTotalCount(Long jobId, long totalCount);
}
