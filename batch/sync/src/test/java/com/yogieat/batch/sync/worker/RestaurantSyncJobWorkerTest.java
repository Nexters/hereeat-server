package com.yogieat.batch.sync.worker;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yogieat.batch.sync.config.SyncJobProperties;
import com.yogieat.restaurant.service.RestaurantRepository;
import com.yogieat.restaurant.sync.service.RestaurantSyncJobRepository;
import com.yogieat.restaurant.sync.service.RestaurantSyncService;
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
}
