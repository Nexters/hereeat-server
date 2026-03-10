package com.yogieat.batch.sync.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 동기화 워커용 Virtual Thread Executor 설정.
 *
 * <p>Virtual Thread를 사용하여 I/O 바운드 동기화 작업의 처리량을 극대화합니다.
 * 외부 API 동시성 제어는 {@code RestaurantSyncService}의 Semaphore가 담당하므로,
 * Executor 레벨에서는 스레드 수 제한 없이 태스크당 Virtual Thread를 생성합니다.</p>
 *
 * <p>Spring의 {@link DisposableBean}을 구현하여 애플리케이션 종료 시
 * 진행 중인 동기화 태스크의 graceful shutdown을 보장합니다.</p>
 */
@Configuration
@Slf4j
public class SyncWorkerConfig implements DisposableBean {

    private static final long GRACEFUL_SHUTDOWN_TIMEOUT_SECONDS = 30;

    private ExecutorService syncJobExecutor;

    @Bean
    public ExecutorService syncJobExecutor() {
        this.syncJobExecutor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("sync-worker-", 0).factory()
        );
        return this.syncJobExecutor;
    }

    @Override
    public void destroy() {
        if (syncJobExecutor == null) {
            return;
        }

        log.info("Shutting down sync job virtual thread executor...");
        syncJobExecutor.shutdown();
        try {
            if (!syncJobExecutor.awaitTermination(GRACEFUL_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("Sync job executor did not terminate within {}s, forcing shutdown",
                        GRACEFUL_SHUTDOWN_TIMEOUT_SECONDS);
                syncJobExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.warn("Interrupted while waiting for sync job executor shutdown");
            syncJobExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("Sync job executor shutdown complete");
    }
}
