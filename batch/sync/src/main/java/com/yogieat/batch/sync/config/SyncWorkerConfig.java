package com.yogieat.batch.sync.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class SyncWorkerConfig {

    private static final long GRACEFUL_SHUTDOWN_TIMEOUT_SECONDS = 30;

    @Bean(destroyMethod = "")
    public ExecutorService syncJobExecutor(SyncJobProperties syncJobProperties) {
        ExecutorService executor = Executors.newFixedThreadPool(syncJobProperties.resolvedParallelism());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down sync job executor gracefully...");
            executor.shutdown();
            try {
                if (!executor.awaitTermination(GRACEFUL_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    log.warn("Sync job executor did not terminate within {}s, forcing shutdown",
                            GRACEFUL_SHUTDOWN_TIMEOUT_SECONDS);
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.warn("Interrupted while waiting for sync job executor shutdown");
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("Sync job executor shutdown complete");
        }, "sync-executor-shutdown"));

        return executor;
    }
}
