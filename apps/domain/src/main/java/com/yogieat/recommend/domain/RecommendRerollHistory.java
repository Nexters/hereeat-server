package com.yogieat.recommend.domain;

import java.time.LocalDateTime;
import java.util.List;

public record RecommendRerollHistory(
        Long id,
        Long gatheringId,
        List<Long> excludedRestaurantIds,
        List<Result> rerolledResults,
        LocalDateTime createdAt
) {
    public record Result(
            Integer rank,
            Long restaurantId,
            Double agreementRate,
            String reasonText
    ) {
        public static Result of(Integer rank, Long restaurantId, Double agreementRate, String reasonText) {
            return new Result(rank, restaurantId, agreementRate, reasonText);
        }
    }

    public record Create(
            Long gatheringId,
            List<Long> excludedRestaurantIds,
            List<Result> rerolledResults
    ) {
        public static RecommendRerollHistory of(
                Long gatheringId,
                List<Long> excludedRestaurantIds,
                List<Result> rerolledResults
        ) {
            return new RecommendRerollHistory(
                    null,
                    gatheringId,
                    excludedRestaurantIds == null ? List.of() : List.copyOf(excludedRestaurantIds),
                    rerolledResults == null ? List.of() : List.copyOf(rerolledResults),
                    null
            );
        }
    }
}
