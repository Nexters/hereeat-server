package com.yogieat.restaurant.sync.service;

import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import com.yogieat.restaurant.service.RestaurantRepository;
import com.yogieat.restaurant.sync.domain.RestaurantSyncJob;
import com.yogieat.restaurant.sync.domain.value.RestaurantSyncJobStatus;
import com.yogieat.restaurant.sync.domain.value.RestaurantSyncScope;
import com.yogieat.restaurant.sync.domain.value.RestaurantSyncTriggerType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RestaurantSyncJobService {

    private final RestaurantSyncJobRepository syncJobRepository;
    private final RestaurantRepository restaurantRepository;
    @Value("${sync.job.chunk-size:50}")
    private int chunkSize;
    @Value("${sync.job.parallelism:4}")
    private int parallelism;

    @Transactional
    public RestaurantSyncJob createAllJob(RestaurantSyncTriggerType triggerType) {
        if (syncJobRepository.existsByScopeAndStatus(RestaurantSyncScope.ALL, RestaurantSyncJobStatus.RUNNING)
                || syncJobRepository.existsByScopeAndStatus(RestaurantSyncScope.ALL, RestaurantSyncJobStatus.PENDING)) {
            throw new CustomException(ErrorCode.SYNC_JOB_CONFLICT);
        }

        RestaurantSyncJob syncJob = RestaurantSyncJob.create(RestaurantSyncScope.ALL, triggerType, null, chunkSize, parallelism);
        return syncJobRepository.save(syncJob);
    }

    @Transactional
    public RestaurantSyncJob createSingleJob(Long restaurantId, RestaurantSyncTriggerType triggerType) {
        restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESTAURANT_NOT_FOUND));

        if (syncJobRepository.existsByTargetRestaurantIdAndStatus(restaurantId, RestaurantSyncJobStatus.RUNNING)
                || syncJobRepository.existsByTargetRestaurantIdAndStatus(restaurantId, RestaurantSyncJobStatus.PENDING)) {
            throw new CustomException(ErrorCode.SYNC_JOB_CONFLICT);
        }

        RestaurantSyncJob syncJob = RestaurantSyncJob.create(RestaurantSyncScope.SINGLE, triggerType, restaurantId, chunkSize, parallelism);
        return syncJobRepository.save(syncJob);
    }

    @Transactional(readOnly = true)
    public RestaurantSyncJob getJob(Long jobId) {
        return syncJobRepository.findById(jobId)
                .orElseThrow(() -> new CustomException(ErrorCode.SYNC_JOB_NOT_FOUND));
    }
}
