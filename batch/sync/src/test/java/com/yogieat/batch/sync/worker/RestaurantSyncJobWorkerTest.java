package com.yogieat.batch.sync.worker;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yogieat.batch.sync.config.SyncJobProperties;
import com.yogieat.restaurant.service.RestaurantRepository;
import com.yogieat.restaurant.sync.domain.RestaurantSyncChunkResult;
import com.yogieat.restaurant.sync.domain.RestaurantSyncJob;
import com.yogieat.restaurant.sync.domain.value.RestaurantSyncFieldUpdatePolicy;
import com.yogieat.restaurant.sync.domain.value.RestaurantSyncJobStatus;
import com.yogieat.restaurant.sync.domain.value.RestaurantSyncScope;
import com.yogieat.restaurant.sync.domain.value.RestaurantSyncTriggerType;
import com.yogieat.restaurant.sync.service.RestaurantSyncJobRepository;
import com.yogieat.restaurant.sync.service.RestaurantSyncService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RestaurantSyncJobWorkerTest {

    @Mock
    private RestaurantSyncJobRepository syncJobRepository;

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private RestaurantSyncService restaurantSyncService;

    @Mock
    private SyncJobProperties syncJobProperties;

    @Mock
    private ExecutorService syncJobExecutor;

    @InjectMocks
    private RestaurantSyncJobWorker worker;

    @Test
    void pollPendingJob_shouldCleanupStaleRunningJobs_beforeClaimingPendingJob() {
        when(syncJobProperties.resolvedStaleRunningThresholdMinutes()).thenReturn(60L);
        when(syncJobRepository.failStaleRunningJobs(any(), eq("stale RUNNING job recovered by worker"))).thenReturn(1);
        when(syncJobRepository.claimNextPendingJob()).thenReturn(Optional.empty());

        worker.pollPendingJob();

        verify(syncJobRepository).failStaleRunningJobs(any(), eq("stale RUNNING job recovered by worker"));
        verify(syncJobRepository).claimNextPendingJob();
        verify(syncJobRepository, never()).markFailed(any(), any());
    }

    @Test
    void pollPendingJob_shouldUseConfiguredParallelismForAllJob() {
        RestaurantSyncJob job = new RestaurantSyncJob(
                10L,
                RestaurantSyncScope.ALL,
                RestaurantSyncTriggerType.MANUAL,
                null,
                RestaurantSyncJobStatus.RUNNING,
                50,
                3,
                null,
                0L,
                0L,
                0L,
                0L,
                null,
                LocalDateTime.now(),
                null,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        when(syncJobProperties.resolvedStaleRunningThresholdMinutes()).thenReturn(60L);
        when(syncJobProperties.resolvedChunkSize()).thenReturn(50);
        when(syncJobRepository.claimNextPendingJob()).thenReturn(Optional.of(job));
        when(restaurantRepository.countActiveRestaurants()).thenReturn(1L);
        when(restaurantRepository.findActiveRestaurantIdsAfter(null, 50)).thenReturn(List.of(1L));
        when(restaurantRepository.findActiveRestaurantIdsAfter(1L, 50)).thenReturn(List.of());
        when(restaurantSyncService.syncChunk(
                eq(List.of(1L)),
                eq(syncJobExecutor),
                eq(3),
                eq(RestaurantSyncFieldUpdatePolicy.UPDATE_ALL)
        ))
                .thenReturn(RestaurantSyncChunkResult.of(1, 1, 0, List.of()));

        worker.pollPendingJob();

        verify(restaurantSyncService).syncChunk(
                eq(List.of(1L)),
                eq(syncJobExecutor),
                eq(3),
                eq(RestaurantSyncFieldUpdatePolicy.UPDATE_ALL)
        );
        verify(syncJobRepository).markSuccess(10L);
    }

    @Test
    void pollPendingJob_shouldPreserveAdminEditableFieldsForScheduledJob() {
        RestaurantSyncJob job = new RestaurantSyncJob(
                10L,
                RestaurantSyncScope.ALL,
                RestaurantSyncTriggerType.SCHEDULED,
                null,
                RestaurantSyncJobStatus.RUNNING,
                50,
                3,
                null,
                0L,
                0L,
                0L,
                0L,
                null,
                LocalDateTime.now(),
                null,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        when(syncJobProperties.resolvedStaleRunningThresholdMinutes()).thenReturn(60L);
        when(syncJobProperties.resolvedChunkSize()).thenReturn(50);
        when(syncJobRepository.claimNextPendingJob()).thenReturn(Optional.of(job));
        when(restaurantRepository.countActiveRestaurants()).thenReturn(1L);
        when(restaurantRepository.findActiveRestaurantIdsAfter(null, 50)).thenReturn(List.of(1L));
        when(restaurantRepository.findActiveRestaurantIdsAfter(1L, 50)).thenReturn(List.of());
        when(restaurantSyncService.syncChunk(
                eq(List.of(1L)),
                eq(syncJobExecutor),
                eq(3),
                eq(RestaurantSyncFieldUpdatePolicy.PRESERVE_ADMIN_EDITABLE)
        ))
                .thenReturn(RestaurantSyncChunkResult.of(1, 1, 0, List.of()));

        worker.pollPendingJob();

        verify(restaurantSyncService).syncChunk(
                eq(List.of(1L)),
                eq(syncJobExecutor),
                eq(3),
                eq(RestaurantSyncFieldUpdatePolicy.PRESERVE_ADMIN_EDITABLE)
        );
        verify(syncJobRepository).markSuccess(10L);
    }
}
