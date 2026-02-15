package com.yogieat.datasource.db.core.restaurant.sync;

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
        RestaurantSyncJobEntity entity = syncJobJpaRepository.findById(jobId).orElseThrow();
        entity.markRunning();
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
        RestaurantSyncJobEntity entity = syncJobJpaRepository.findById(jobId).orElseThrow();
        entity.updateProgress(lastProcessedRestaurantId, processedIncrement, successIncrement, failedIncrement);
    }

    @Override
    @Transactional
    public void markSuccess(Long jobId) {
        RestaurantSyncJobEntity entity = syncJobJpaRepository.findById(jobId).orElseThrow();
        entity.markSuccess();
    }

    @Override
    @Transactional
    public void markPartialFailed(Long jobId, String errorSummary) {
        RestaurantSyncJobEntity entity = syncJobJpaRepository.findById(jobId).orElseThrow();
        entity.markPartialFailed(errorSummary);
    }

    @Override
    @Transactional
    public void markFailed(Long jobId, String errorSummary) {
        RestaurantSyncJobEntity entity = syncJobJpaRepository.findById(jobId).orElseThrow();
        entity.markFailed(errorSummary);
    }

    @Override
    @Transactional
    public void initializeTotalCount(Long jobId, long totalCount) {
        RestaurantSyncJobEntity entity = syncJobJpaRepository.findById(jobId).orElseThrow();
        entity.initializeTotalCount(totalCount);
    }
}
