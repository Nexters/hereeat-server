package com.yogieat.recommend.domain.command;

import java.util.List;

public record RecommendCommand() {

    public record Proceed(
            String accessKey
    ) {
        public static Proceed of(String accessKey) {
            return new Proceed(accessKey);
        }
    }

    public record Reroll(
            String accessKey,
            List<Long> restaurantIds
    ) {
        public static Reroll of(String accessKey, List<Long> restaurantIds) {
            return new Reroll(
                    accessKey,
                    restaurantIds == null ? List.of() : List.copyOf(restaurantIds)
            );
        }
    }
}
