package com.yogieat.recommend.domain;

import com.yogieat.recommend.domain.value.RecommendStatus;

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
        Double score,
        // 추천 근거 텍스트
        String reasonText
) {
    // Create
    public record Create(
            Long gatheringId,
            Long restaurantId,
            Double agreementRate,
            RecommendStatus status,
            Integer rank,
            Double score,
            String reasonText
    ) {
        // static factory method
        public static RecommendResult of(Long gatheringId,
                                  Long restaurantId,
                                  Double agreementRate,
                                  RecommendStatus status,
                                  Integer rank,
                                  Double score,
                                  String reasonText) {
            return new RecommendResult(null, gatheringId, restaurantId, agreementRate, status, rank, score, reasonText);
        }
    }
}
