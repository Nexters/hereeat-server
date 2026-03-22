package com.yogieat.recommend.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import com.yogieat.recommend.domain.RecommendResult;
import com.yogieat.recommend.domain.value.RecommendStatus;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecommendValidatorTest {

    private final RecommendValidator recommendValidator = new RecommendValidator();

    @Test
    @DisplayName("완료된 추천 결과가 있으면 재추천 검증을 통과한다")
    void validateRerollAvailable_WhenCompletedResultsExist_ShouldPass() {
        List<RecommendResult> recommendResults = List.of(
                RecommendResult.Create.of(1L, 10L, 80.0, RecommendStatus.COMPLETED, 1, 4.5, "reason")
        );

        assertThatCode(() -> recommendValidator.validateRerollAvailable(recommendResults))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("추천 결과가 없으면 RECOMMEND_RESULT_NOT_FOUND 예외가 발생한다")
    void validateRerollAvailable_WhenNoResults_ShouldThrowNotFound() {
        assertThatThrownBy(() -> recommendValidator.validateRerollAvailable(List.of()))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RECOMMEND_RESULT_NOT_FOUND);
    }

    @Test
    @DisplayName("추천이 완료되지 않았으면 RECOMMEND_REROLL_NOT_AVAILABLE 예외가 발생한다")
    void validateRerollAvailable_WhenResultStatusIsNotCompleted_ShouldThrowConflict() {
        List<RecommendResult> recommendResults = List.of(
                RecommendResult.Create.of(1L, null, 0.0, RecommendStatus.PENDING, null, 0.0, null)
        );

        assertThatThrownBy(() -> recommendValidator.validateRerollAvailable(recommendResults))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RECOMMEND_REROLL_NOT_AVAILABLE);
    }
}
