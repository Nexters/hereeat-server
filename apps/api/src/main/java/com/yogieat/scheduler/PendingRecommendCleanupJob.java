package com.yogieat.scheduler;

import com.yogieat.recommend.domain.RecommendResult;
import com.yogieat.recommend.domain.RecommendResultFailed;
import com.yogieat.recommend.domain.value.FailureReason;
import com.yogieat.recommend.domain.value.RecommendStatus;
import com.yogieat.recommend.service.RecommendResultFailedRepository;
import com.yogieat.recommend.service.RecommendResultRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class PendingRecommendCleanupJob {

    private final RecommendResultRepository recommendResultRepository;
    private final RecommendResultFailedRepository recommendResultFailedRepository;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    /**
     * 1분마다 실행하여 1분 이상 PENDING 상태인 레코드를 FAILED로 전환
     * fixedDelay = 60000ms (1분)
     */
    @Scheduled(fixedDelay = 60000)
    @Transactional
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

            int successCount = 0;
            int failureCount = 0;

            // 각 PENDING 레코드를 FAILED로 전환
            for (RecommendResult pending : orphanedPending) {
                try {
                    Long gatheringId = pending.gatheringId();

                    // 1. PENDING 삭제
                    recommendResultRepository.deleteByGatheringId(gatheringId);

                    // 2. t_recommend_result에 FAILED 레코드 생성
                    RecommendResult failedResult = RecommendResult.Create.of(
                        gatheringId,
                        null,
                        0.0,
                        RecommendStatus.FAILED,
                        null,
                        0.0
                    );
                    recommendResultRepository.save(failedResult);

                    // 3. t_recommend_result_failed에 실패 컨텍스트 저장
                    RecommendResultFailed failedContext = RecommendResultFailed.Create.of(
                        gatheringId,
                        FailureReason.ASYNC_PROCESSING_TIMEOUT,
                        "Async processing did not complete within 1 minute",
                        LocalDateTime.now()
                    );
                    recommendResultFailedRepository.save(failedContext);
                    log.info("Cleaned up PENDING recommend result for gatheringId={}", gatheringId);

                    successCount++;
                } catch (Exception e) {
                    log.error("Failed to cleanup PENDING recommend result for gatheringId={}", pending.gatheringId(), e);
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
