package com.yogieat.domain.recommend.domain;

public record RecommendResult(
        Long id,
        Long gatheringId,
        Long restaurantId,
        // 의견 일치율
        Double agreementRate,
        // 추천 처리 상태
        RecommendStatus status,
        // 추천 순위 (1, 2, 3)
        Integer rank,
        // 추천 점수
        Double score
) {
}
