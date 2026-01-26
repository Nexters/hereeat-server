package com.yogieat.domain.recommend.domain.result;

import com.yogieat.domain.category.domain.value.LargeCategory;
import com.yogieat.domain.common.GeoJson;
import com.yogieat.domain.common.Region;
import com.yogieat.domain.participant.domain.value.DistanceRange;
import java.util.List;
import java.util.Map;

public record RecommendResultResult() {

    /**
     * 추천 결과 조회 Result
     */
    public record Get(
            List<Ranking> rankings,
            Map<String, Integer> preferences,
            Map<String, Integer> dislikes,
            Double averageAgreementRate
    ) {
        public static Get of(
                List<Ranking> rankings,
                Map<String, Integer> preferences,
                Map<String, Integer> dislikes,
                Double averageAgreementRate
        ) {
            return new Get(rankings, preferences, dislikes, averageAgreementRate);
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
            Double agreementRate
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
                Double agreementRate
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
                    agreementRate
            );
        }
    }
}
