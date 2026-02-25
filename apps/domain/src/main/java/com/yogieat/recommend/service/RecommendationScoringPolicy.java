package com.yogieat.recommend.service;

/**
 * 추천 점수 계산에 사용하는 튜닝 파라미터 모음입니다.
 */
public record RecommendationScoringPolicy(
        double distanceBonus,
        double diversityBonus,
        AiSummary aiSummary,
        ColdStart coldStart,
        Freshness freshness,
        Credibility credibility,
        Candidate candidate
) {

    public static final RecommendationScoringPolicy DEFAULT = new RecommendationScoringPolicy(
            1.0,
            0.5,
            new AiSummary(0.5, 0.3, -0.2, 4),
            new ColdStart(30, 10, 4.0, 0.3),
            new Freshness(7, 30, 90, 0.3, 0.1, -0.2),
            new Credibility(1.5, 5.0, 3.0, 5.0),
            new Candidate(10, 3)
    );

    public record AiSummary(
            double groupBoost,
            double positiveBoost,
            double negativePenalty,
            int groupSizeThreshold
    ) {
    }

    public record ColdStart(
            int daysThreshold,
            int reviewThreshold,
            double ratingThreshold,
            double boost
    ) {
    }

    public record Freshness(
            int recentDays,
            int moderateDays,
            int staleDays,
            double recentBoost,
            double moderateBoost,
            double stalePenalty
    ) {
    }

    public record Credibility(
            double blogReviewWeightMultiplier,
            double maxReviewWeight,
            double ratingMin,
            double ratingMax
    ) {
    }

    public record Candidate(
            int poolSize,
            int topKSize
    ) {
    }
}
