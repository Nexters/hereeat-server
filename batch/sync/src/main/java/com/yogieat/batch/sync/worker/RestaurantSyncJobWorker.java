package com.yogieat.batch.sync.worker;

import com.yogieat.batch.sync.config.SyncJobProperties;
import com.yogieat.restaurant.service.RestaurantRepository;
import com.yogieat.restaurant.sync.domain.RestaurantSyncChunkResult;
import com.yogieat.restaurant.sync.domain.RestaurantSyncJob;
import com.yogieat.restaurant.sync.domain.value.RestaurantSyncScope;
import com.yogieat.restaurant.sync.service.RestaurantSyncJobRepository;
import com.yogieat.restaurant.sync.service.RestaurantSyncService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RestaurantSyncJobWorker {
    private static final String STALE_RUNNING_ERROR_SUMMARY = "stale RUNNING job recovered by worker";

    private final RestaurantSyncJobRepository syncJobRepository;
    private final RestaurantRepository restaurantRepository;
    private final RestaurantSyncService restaurantSyncService;
    private final SyncJobProperties syncJobProperties;
    private final ExecutorService syncJobExecutor;

    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(fixedDelayString = "${sync.job.poll-delay-ms:10000}")
    public void pollPendingJob() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        RestaurantSyncJob currentJob = null;
        try {
            cleanupStaleRunningJobs();

            currentJob = syncJobRepository.claimNextPendingJob().orElse(null);
            if (currentJob == null) {
                return;
            }

            if (currentJob.scope() == RestaurantSyncScope.SINGLE) {
                executeSingle(currentJob);
                return;
            }

            executeAll(currentJob);
        } catch (Exception e) {
            log.error("Sync job execution failed", e);
            if (currentJob != null) {
                syncJobRepository.markFailed(currentJob.id(), e.getMessage());
            }
        } finally {
            running.set(false);
        }
    }

    private void cleanupStaleRunningJobs() {
        Duration threshold = Duration.ofMinutes(syncJobProperties.resolvedStaleRunningThresholdMinutes());
        int recovered = syncJobRepository.failStaleRunningJobs(threshold, STALE_RUNNING_ERROR_SUMMARY);
        if (recovered > 0) {
            log.warn("Recovered {} stale RUNNING sync jobs (threshold={}m)",
                    recovered,
                    threshold.toMinutes());
        }
    }

    private void executeSingle(RestaurantSyncJob job) {
        if (job.targetRestaurantId() == null) {
            syncJobRepository.markFailed(job.id(), "targetRestaurantId is null");
            return;
        }

        syncJobRepository.initializeTotalCount(job.id(), 1L);

        RestaurantSyncChunkResult result = restaurantSyncService.syncChunk(
                List.of(job.targetRestaurantId()),
                syncJobExecutor
        );
        syncJobRepository.updateProgress(
                job.id(),
                job.targetRestaurantId(),
                result.processedCount(),
                result.successCount(),
                result.failedCount()
        );

        if (result.failedCount() == 0) {
            syncJobRepository.markSuccess(job.id());
        } else {
            String errorMessage = result.errorMessages().isEmpty()
                    ? "single sync failed"
                    : result.errorMessages().getFirst();
            syncJobRepository.markPartialFailed(job.id(), errorMessage);
        }
    }

    private void executeAll(RestaurantSyncJob job) {
        long totalCount = restaurantRepository.countActiveRestaurants();
        syncJobRepository.initializeTotalCount(job.id(), totalCount);

        long lastId = job.lastProcessedRestaurantId() == null ? 0L : job.lastProcessedRestaurantId();
        long totalFailed = 0L;
        List<String> errorMessages = new ArrayList<>();

        while (true) {
            List<Long> ids = restaurantRepository.findActiveRestaurantIdsAfter(lastId == 0L ? null : lastId, syncJobProperties.resolvedChunkSize());
            if (ids.isEmpty()) {
                break;
            }

            RestaurantSyncChunkResult chunkResult = restaurantSyncService.syncChunk(ids, syncJobExecutor);
            long successCount = chunkResult.successCount();
            long failedCount = chunkResult.failedCount();
            totalFailed += failedCount;

            if (failedCount > 0 && errorMessages.size() < 10) {
                chunkResult.errorMessages().stream()
                        .limit(10 - errorMessages.size())
                        .forEach(errorMessages::add);
            }

            lastId = ids.getLast();

            syncJobRepository.updateProgress(
                    job.id(),
                    lastId,
                    ids.size(),
                    successCount,
                    failedCount
            );
        }

        if (totalFailed > 0) {
            String summary = errorMessages.isEmpty() ? "partial failures" : String.join(" | ", errorMessages);
            syncJobRepository.markPartialFailed(job.id(), summary);
        } else {
            syncJobRepository.markSuccess(job.id());
        }
    }
}
