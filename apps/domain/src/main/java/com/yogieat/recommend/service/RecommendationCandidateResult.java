package com.yogieat.recommend.service;

import com.yogieat.recommend.domain.value.FailureReason;
import com.yogieat.recommend.domain.value.ScoredRestaurant;
import java.util.List;

public record RecommendationCandidateResult(
        List<ScoredRestaurant> restaurants,
        FailureReason failureReason,
        String failureMessage
) {
    public static RecommendationCandidateResult success(List<ScoredRestaurant> restaurants) {
        return new RecommendationCandidateResult(
                restaurants == null ? List.of() : List.copyOf(restaurants),
                null,
                null
        );
    }

    public static RecommendationCandidateResult failure(FailureReason failureReason, String failureMessage) {
        return new RecommendationCandidateResult(List.of(), failureReason, failureMessage);
    }

    public boolean failed() {
        return failureReason != null;
    }
}
