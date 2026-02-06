package com.yogieat.recommend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yogieat.recommend.domain.RecommendResult;
import com.yogieat.recommend.domain.RecommendResultFailed;
import com.yogieat.recommend.domain.value.RecommendStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PendingRecordCleanupProcessorTest {

    @Mock
    private RecommendResultRepository recommendResultRepository;

    @Mock
    private RecommendResultFailedRepository recommendResultFailedRepository;

    @InjectMocks
    private PendingRecordCleanupProcessor processor;

    @Test
    @DisplayName("PENDING 레코드를 성공적으로 정리한다")
    void shouldCleanupSinglePendingRecord_Success() {
        // Given: PENDING 상태의 추천 결과
        RecommendResult pending = RecommendResult.Create.of(
            1L,
            null,
            0.0,
            RecommendStatus.PENDING,
            null,
            0.0
        );

        // When: 레코드 정리 실행
        boolean result = processor.cleanupSinglePendingRecord(pending);

        // Then: 성공 반환 및 모든 작업 수행 확인
        assertThat(result).isTrue();

        // 1. PENDING 삭제
        verify(recommendResultRepository, times(1)).deleteByGatheringId(1L);

        // 2. FAILED 레코드 저장
        verify(recommendResultRepository, times(1)).save(any(RecommendResult.class));

        // 3. 실패 컨텍스트 저장
        verify(recommendResultFailedRepository, times(1)).save(any(RecommendResultFailed.class));
    }

    @Test
    @DisplayName("삭제 중 예외 발생 시 false를 반환한다")
    void shouldReturnFalse_WhenDeleteFails() {
        // Given: PENDING 레코드와 삭제 중 예외 발생 설정
        RecommendResult pending = RecommendResult.Create.of(
            2L,
            null,
            0.0,
            RecommendStatus.PENDING,
            null,
            0.0
        );

        doThrow(new RuntimeException("DB connection error"))
            .when(recommendResultRepository)
            .deleteByGatheringId(2L);

        // When: 레코드 정리 실행
        boolean result = processor.cleanupSinglePendingRecord(pending);

        // Then: 실패 반환
        assertThat(result).isFalse();

        // save는 호출되지 않음 (예외 발생으로 중단)
        verify(recommendResultRepository, times(0)).save(any(RecommendResult.class));
        verify(recommendResultFailedRepository, times(0)).save(any(RecommendResultFailed.class));
    }

    @Test
    @DisplayName("FAILED 레코드 저장 중 예외 발생 시 false를 반환한다")
    void shouldReturnFalse_WhenSaveFails() {
        // Given: PENDING 레코드와 save 중 예외 발생 설정
        RecommendResult pending = RecommendResult.Create.of(
            3L,
            null,
            0.0,
            RecommendStatus.PENDING,
            null,
            0.0
        );

        when(recommendResultRepository.save(any(RecommendResult.class)))
            .thenThrow(new RuntimeException("Transaction timeout"));

        // When: 레코드 정리 실행
        boolean result = processor.cleanupSinglePendingRecord(pending);

        // Then: 실패 반환
        assertThat(result).isFalse();

        // delete는 실행되지만 실패 컨텍스트는 저장되지 않음 (예외 발생으로 중단)
        verify(recommendResultRepository, times(1)).deleteByGatheringId(3L);
        verify(recommendResultFailedRepository, times(0)).save(any(RecommendResultFailed.class));
    }

    @Test
    @DisplayName("실패 컨텍스트 저장 중 예외 발생 시 false를 반환한다")
    void shouldReturnFalse_WhenFailedContextSaveFails() {
        // Given: PENDING 레코드와 실패 컨텍스트 저장 중 예외 발생 설정
        RecommendResult pending = RecommendResult.Create.of(
            4L,
            null,
            0.0,
            RecommendStatus.PENDING,
            null,
            0.0
        );

        when(recommendResultFailedRepository.save(any(RecommendResultFailed.class)))
            .thenThrow(new RuntimeException("Failed context save error"));

        // When: 레코드 정리 실행
        boolean result = processor.cleanupSinglePendingRecord(pending);

        // Then: 실패 반환
        assertThat(result).isFalse();

        // delete와 FAILED 레코드 저장은 실행됨
        verify(recommendResultRepository, times(1)).deleteByGatheringId(4L);
        verify(recommendResultRepository, times(1)).save(any(RecommendResult.class));
    }
}
