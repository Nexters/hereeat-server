package com.yogieat.scheduler;

import com.yogieat.recommend.domain.RecommendResult;
import com.yogieat.recommend.service.PendingRecordCleanupProcessor;
import com.yogieat.recommend.service.RecommendResultRepository;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PendingRecommendCleanupJob {

    private final RecommendResultRepository recommendResultRepository;
    private final PendingRecordCleanupProcessor cleanupProcessor;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    /**
     * 10분마다 실행하여 1분 이상 PENDING 상태인 레코드를 FAILED로 전환
     * fixedDelay = 600000ms (10분)
     *
     * 각 레코드는 독립적인 트랜잭션으로 처리되어 부분 실패 시에도 성공한 레코드는 커밋됩니다.
     */
    @Scheduled(fixedDelay = 600000)
    public void cleanupOrphanedPendingRecords() {
        // 중복 실행 방지
        if (!isRunning.compareAndSet(false, true)) {
            return;
        }

        try {
            // 1분 이상 된 PENDING 레코드 조회
            List<RecommendResult> orphanedPending =
                recommendResultRepository.findOrphanedPending(Duration.ofMinutes(1));

            if (orphanedPending.isEmpty()) {
                return;
            }

            int failureCount = 0;

            // 각 PENDING 레코드를 독립적인 트랜잭션으로 FAILED로 전환
            for (RecommendResult pending : orphanedPending) {
                boolean success = cleanupProcessor.cleanupSinglePendingRecord(pending);
                if (!success) {
                    failureCount++;
                }
            }

            // 실패가 많으면 알림
            if (failureCount > 0) {
                log.error("ALERT: {} cleanup operations failed. Please investigate.",
                          failureCount);
            }

        } catch (Exception e) {
            log.error("Error during PENDING cleanup job", e);
        } finally {
            isRunning.set(false);
        }
    }
}
