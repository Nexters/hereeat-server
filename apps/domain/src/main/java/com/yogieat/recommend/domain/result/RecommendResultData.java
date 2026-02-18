package com.yogieat.recommend.domain.result;

import com.yogieat.category.domain.value.LargeCategory;
import com.yogieat.common.GeoJson;
import com.yogieat.common.Region;
import com.yogieat.participant.domain.value.DistanceRange;
import com.yogieat.recommend.domain.value.RecommendStatus;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public record RecommendResultData() {

    /**
     * 추천 결과 조회 Result
     */
    public record Get(
            RecommendStatus status,
            List<Ranking> rankings,
            Map<String, Integer> preferences,
            Map<String, Integer> dislikes,
            Map<String, Integer> distances,
            Double averageAgreementRate
    ) {
        public static Get of(
                RecommendStatus status,
                List<Ranking> rankings,
                Map<String, Integer> preferences,
                Map<String, Integer> dislikes,
                Map<String, Integer> distances,
                Double averageAgreementRate
        ) {
            return new Get(status, rankings, preferences, dislikes, distances, averageAgreementRate);
        }

        public static Get ofPending() {
            return new Get(
                RecommendStatus.PENDING,
                Collections.emptyList(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                0.0
            );
        }

        public static Get ofEmpty() {
            return new Get(
                null,
                Collections.emptyList(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                0.0
            );
        }
    }

    /**
     * 추천 결과 랭킹 정보
     */
    public record Ranking(
            Integer rank,
            Long restaurantId,
            String restaurantName,
            String address,
            Double rating,
            String imageUrl,
            String mapUrl,
            String representativeReview,
            String description,
            Region region,
            GeoJson.Point location,
            LargeCategory largeCategory,
            String mediumCategory,
            DistanceRange majorityDistanceRange,
            // 추천 근거 데이터 (신규 필드)
            Integer reviewCount,
            Integer blogReviewCount,
            String representMenu,
            Integer representMenuPrice,
            String priceLevel,
            String aiMateSummaryTitle,
            List<String> aiMateSummaryContents,
            // 추천 근거 텍스트 (신규)
            String reasonText
    ) {
        public static Ranking of(
                Integer rank,
                Long restaurantId,
                String restaurantName,
                String address,
                Double rating,
                String imageUrl,
                String mapUrl,
                String representativeReview,
                String description,
                Region region,
                GeoJson.Point location,
                LargeCategory largeCategory,
                String mediumCategory,
                DistanceRange majorityDistanceRange,
                // 추천 근거 데이터
                Integer reviewCount,
                Integer blogReviewCount,
                String representMenu,
                Integer representMenuPrice,
                String priceLevel,
                String aiMateSummaryTitle,
                List<String> aiMateSummaryContents,
                // 추천 근거 텍스트
                String reasonText
        ) {
            return new Ranking(
                    rank,
                    restaurantId,
                    restaurantName,
                    address,
                    rating,
                    imageUrl,
                    mapUrl,
                    representativeReview,
                    description,
                    region,
                    location,
                    largeCategory,
                    mediumCategory,
                    majorityDistanceRange,
                    reviewCount,
                    blogReviewCount,
                    representMenu,
                    representMenuPrice,
                    priceLevel,
                    aiMateSummaryTitle,
                    aiMateSummaryContents,
                    reasonText
            );
        }
    }
}
