package com.yogieat.datasource.db.core.restaurant.sync;

import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import com.yogieat.restaurant.sync.domain.RestaurantSyncJob;
import com.yogieat.restaurant.sync.domain.value.RestaurantSyncJobStatus;
import com.yogieat.restaurant.sync.domain.value.RestaurantSyncScope;
import com.yogieat.restaurant.sync.service.RestaurantSyncJobRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class RestaurantSyncJobCoreRepository implements RestaurantSyncJobRepository {

    private final RestaurantSyncJobJpaRepository syncJobJpaRepository;

    @Override
    public RestaurantSyncJob save(RestaurantSyncJob syncJob) {
        RestaurantSyncJobEntity saved = syncJobJpaRepository.save(RestaurantSyncJobEntity.from(syncJob));
        return RestaurantSyncJobEntity.toDomain(saved);
    }

    @Override
    public Optional<RestaurantSyncJob> findById(Long id) {
        return syncJobJpaRepository.findById(id).map(RestaurantSyncJobEntity::toDomain);
    }

    @Override
    public Optional<RestaurantSyncJob> findTopByStatusOrderByCreatedAtAsc(RestaurantSyncJobStatus status) {
        return syncJobJpaRepository.findTopByStatusOrderByCreatedAtAsc(status)
                .map(RestaurantSyncJobEntity::toDomain);
    }

    @Override
    public boolean existsByScopeAndStatus(RestaurantSyncScope scope, RestaurantSyncJobStatus status) {
        return syncJobJpaRepository.existsByScopeAndStatus(scope, status);
    }

    @Override
    public boolean existsByTargetRestaurantIdAndStatus(Long targetRestaurantId, RestaurantSyncJobStatus status) {
        return syncJobJpaRepository.existsByTargetRestaurantIdAndStatus(targetRestaurantId, status);
    }

    @Override
    @Transactional
    public void markRunning(Long jobId) {
        getJobEntityOrThrow(jobId).markRunning();
    }

    @Override
    @Transactional
    public void updateProgress(
            Long jobId,
            Long lastProcessedRestaurantId,
            long processedIncrement,
            long successIncrement,
            long failedIncrement
    ) {
        getJobEntityOrThrow(jobId).updateProgress(
                lastProcessedRestaurantId,
                processedIncrement,
                successIncrement,
                failedIncrement
        );
    }

    @Override
    @Transactional
    public void markSuccess(Long jobId) {
        getJobEntityOrThrow(jobId).markSuccess();
    }

    @Override
    @Transactional
    public void markPartialFailed(Long jobId, String errorSummary) {
        getJobEntityOrThrow(jobId).markPartialFailed(errorSummary);
    }

    @Override
    @Transactional
    public void markFailed(Long jobId, String errorSummary) {
        getJobEntityOrThrow(jobId).markFailed(errorSummary);
    }

    @Override
    @Transactional
    public void initializeTotalCount(Long jobId, long totalCount) {
        getJobEntityOrThrow(jobId).initializeTotalCount(totalCount);
    }

    private RestaurantSyncJobEntity getJobEntityOrThrow(Long jobId) {
        return syncJobJpaRepository.findById(jobId)
                .orElseThrow(() -> new CustomException(ErrorCode.SYNC_JOB_NOT_FOUND));
    }
}
