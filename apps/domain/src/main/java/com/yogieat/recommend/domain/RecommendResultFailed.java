package com.yogieat.recommend.domain;

import java.time.LocalDateTime;

public record RecommendResultFailed(
        Long id,
        Long gatheringId,
        FailureReason failureReason,
        String errorMessage,
        LocalDateTime failedAt
) {
    public record Create(
            Long gatheringId,
            FailureReason failureReason,
            String errorMessage,
            LocalDateTime failedAt
    ) {
        public static RecommendResultFailed of(
                Long gatheringId,
                FailureReason failureReason,
                String errorMessage,
                LocalDateTime failedAt
        ) {
            return new RecommendResultFailed(
                    null,
                    gatheringId,
                    failureReason,
                    errorMessage,
                    failedAt
            );
        }
    }
}
