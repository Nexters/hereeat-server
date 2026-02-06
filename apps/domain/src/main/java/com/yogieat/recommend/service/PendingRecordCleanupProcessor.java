package com.yogieat.recommend.service;

import com.yogieat.recommend.domain.RecommendResult;
import com.yogieat.recommend.domain.RecommendResultFailed;
import com.yogieat.recommend.domain.value.FailureReason;
import com.yogieat.recommend.domain.value.RecommendStatus;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PendingRecordCleanupProcessor {

    private final RecommendResultRepository recommendResultRepository;
    private final RecommendResultFailedRepository recommendResultFailedRepository;

    /**
     * 단일 PENDING 레코드를 독립적인 트랜잭션으로 정리합니다.
     * REQUIRES_NEW를 사용하여 각 레코드가 독립적으로 커밋되도록 보장합니다.
     *
     * @param pending 정리할 PENDING 레코드
     * @return 정리 성공 시 true, 실패 시 false
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean cleanupSinglePendingRecord(RecommendResult pending) {
        try {
            Long gatheringId = pending.gatheringId();

            // 1. PENDING 레코드 삭제
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
            return true;

        } catch (Exception e) {
            log.error("Failed to cleanup PENDING recommend result for gatheringId={}",
                     pending.gatheringId(), e);
            // 예외 발생 시 이 트랜잭션만 롤백됨 (REQUIRES_NEW)
            return false;
        }
    }
}
