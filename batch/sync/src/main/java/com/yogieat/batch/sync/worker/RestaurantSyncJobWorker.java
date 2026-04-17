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
import java.util.concurrent.ThreadLocalRandom;
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
    private static final int PROGRESS_UPDATE_INTERVAL_CHUNKS = 5;
    private static final long PROGRESS_UPDATE_INTERVAL_MS = 5_000L;
    private static final int PROGRESS_UPDATE_BATCH_FAIL_THRESHOLD = 2;
    private static final long CHUNK_SLOW_THRESHOLD_MS = 8_000L;
    private static final int MIN_CHUNK_SIZE = 20;
    private static final int CHUNK_MAX_RETRY_ATTEMPTS = 2;
    private static final long CHUNK_RETRY_BASE_DELAY_MS = 500L;

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
        long pollStartAt = System.nanoTime();
        try {
            long staleStartAt = System.nanoTime();
            cleanupStaleRunningJobs();
            log.debug("cleanup stale jobs finished in {}ms", (System.nanoTime() - staleStartAt) / 1_000_000L);

            long claimStartAt = System.nanoTime();
            currentJob = syncJobRepository.claimNextPendingJob().orElse(null);
            if (currentJob == null) {
                log.debug("sync job poll finished: no pending job, elapsed {}ms",
                        (System.nanoTime() - pollStartAt) / 1_000_000L);
                return;
            }
            log.debug("claimed sync job {} in {}ms", currentJob.id(), (System.nanoTime() - claimStartAt) / 1_000_000L);

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
            log.debug("sync job poll completed in {}ms (jobId={})",
                    (System.nanoTime() - pollStartAt) / 1_000_000L,
                    currentJob == null ? "none" : currentJob.id());
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
                syncJobExecutor,
                Math.max(1, job.parallelism() == null ? 1 : job.parallelism())
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
        int baseChunkSize = syncJobProperties.resolvedChunkSize();
        int currentChunkSize = Math.max(1, baseChunkSize);
        int minChunkSize = Math.max(MIN_CHUNK_SIZE, Math.max(1, baseChunkSize / 2));
        int maxChunkSize = Math.max(baseChunkSize, baseChunkSize * 2);
        int slowChunkStreak = 0;
        int successChunkStreak = 0;
        syncJobRepository.initializeTotalCount(job.id(), totalCount);

        long lastId = job.lastProcessedRestaurantId() == null ? 0L : job.lastProcessedRestaurantId();
        long totalFailed = 0L;
        long totalSuccess = 0L;
        List<String> errorMessages = new ArrayList<>();
        long lastProgressUpdateAt = System.currentTimeMillis();
        int chunkIndex = 0;
        long totalProcessed = 0L;
        long lastUpdatedProcessed = 0L;
        long lastUpdatedSuccess = 0L;
        long lastUpdatedFailed = 0L;

        while (true) {
            long chunkStartAt = System.nanoTime();
            List<Long> ids = restaurantRepository.findActiveRestaurantIdsAfter(lastId == 0L ? null : lastId, currentChunkSize);
            if (ids.isEmpty()) {
                break;
            }

            RestaurantSyncChunkResult chunkResult = syncChunkWithRetry(
                    ids,
                    Math.max(1, job.parallelism() == null ? syncJobProperties.resolvedParallelism() : job.parallelism())
            );
            long chunkDurationMs = (System.nanoTime() - chunkStartAt) / 1_000_000L;
            long successCount = chunkResult.successCount();
            long failedCount = chunkResult.failedCount();
            totalSuccess += successCount;
            totalFailed += failedCount;
            totalProcessed += ids.size();
            chunkIndex++;
            boolean slowChunk = chunkDurationMs > CHUNK_SLOW_THRESHOLD_MS;
            boolean hasFailure = failedCount > 0;

            if (failedCount > 0 && errorMessages.size() < 10) {
                chunkResult.errorMessages().stream()
                        .limit(10 - errorMessages.size())
                        .forEach(errorMessages::add);
            }

            lastId = ids.getLast();
            long now = System.currentTimeMillis();
            boolean shouldUpdate = chunkIndex % PROGRESS_UPDATE_INTERVAL_CHUNKS == 0
                    || failedCount > 0
                    || now - lastProgressUpdateAt >= PROGRESS_UPDATE_INTERVAL_MS;

            if (shouldUpdate) {
                syncJobRepository.updateProgress(
                        job.id(),
                        lastId,
                        totalProcessed - lastUpdatedProcessed,
                        totalSuccess - lastUpdatedSuccess,
                        totalFailed - lastUpdatedFailed
                );
                lastProgressUpdateAt = now;
                lastUpdatedProcessed = totalProcessed;
                lastUpdatedSuccess = totalSuccess;
                lastUpdatedFailed = totalFailed;
            }

            if (hasFailure || slowChunk) {
                slowChunkStreak++;
                successChunkStreak = 0;
            } else {
                successChunkStreak++;
                slowChunkStreak = 0;
            }

            if (slowChunkStreak >= PROGRESS_UPDATE_BATCH_FAIL_THRESHOLD && currentChunkSize > minChunkSize) {
                int nextChunkSize = Math.max(minChunkSize, currentChunkSize / 2);
                if (nextChunkSize != currentChunkSize) {
                    log.warn(
                            "reducing chunk size due to unstable chunk latency/failure. jobId={} before={} after={}",
                            job.id(),
                            currentChunkSize,
                            nextChunkSize
                    );
                    currentChunkSize = nextChunkSize;
                }
                slowChunkStreak = 0;
            } else if (successChunkStreak >= PROGRESS_UPDATE_BATCH_FAIL_THRESHOLD
                    && currentChunkSize < maxChunkSize) {
                int nextChunkSize = Math.min(maxChunkSize, (int) Math.ceil(currentChunkSize * 1.2));
                if (nextChunkSize != currentChunkSize) {
                    log.info(
                            "increasing chunk size as chunk processed stably. jobId={} before={} after={}",
                            job.id(),
                            currentChunkSize,
                            nextChunkSize
                    );
                    currentChunkSize = nextChunkSize;
                }
                successChunkStreak = 0;
            }

            log.debug(
                    "sync all chunk complete. jobId={} chunkIndex={} chunkDurationMs={} chunkSize={} processed={} success={} failed={} "
                            + "nextChunkSize={}",
                    job.id(),
                    chunkIndex,
                    chunkDurationMs,
                    ids.size(),
                    successCount,
                    failedCount,
                    currentChunkSize
            );
        }

        if (totalProcessed > lastUpdatedProcessed
                || totalSuccess > lastUpdatedSuccess
                || totalFailed > lastUpdatedFailed) {
            syncJobRepository.updateProgress(
                    job.id(),
                    lastId,
                    totalProcessed - lastUpdatedProcessed,
                    totalSuccess - lastUpdatedSuccess,
                    totalFailed - lastUpdatedFailed
            );
        }

        if (totalFailed > 0) {
            String summary = errorMessages.isEmpty() ? "partial failures" : String.join(" | ", errorMessages);
            syncJobRepository.markPartialFailed(job.id(), summary);
        } else {
            syncJobRepository.markSuccess(job.id());
        }
    }

    private RestaurantSyncChunkResult syncChunkWithRetry(List<Long> ids, int parallelism) {
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= CHUNK_MAX_RETRY_ATTEMPTS + 1; attempt++) {
            try {
                return restaurantSyncService.syncChunk(
                        ids,
                        syncJobExecutor,
                        Math.max(1, parallelism)
                );
            } catch (RuntimeException e) {
                lastError = e;
                if (attempt > CHUNK_MAX_RETRY_ATTEMPTS) {
                    break;
                }
                long delay = CHUNK_RETRY_BASE_DELAY_MS * (1L << (attempt - 1));
                long jitterUpperBound = delay / 4;
                long jitter = jitterUpperBound > 0 ? ThreadLocalRandom.current().nextLong(jitterUpperBound) : 0L;
                log.warn("[chunk-retry] attempt={}/{} failed: {}, retrying in {}ms",
                        attempt, CHUNK_MAX_RETRY_ATTEMPTS + 1, e.getMessage(), delay + jitter);
                try {
                    Thread.sleep(delay + jitter);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
        throw lastError;
    }
}
