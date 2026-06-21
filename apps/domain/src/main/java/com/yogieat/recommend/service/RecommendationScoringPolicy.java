package com.yogieat.recommend.service;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 추천 점수 계산에 사용하는 튜닝 파라미터 모음입니다.
 * application.yaml의 recommendation.scoring 프리픽스로 오버라이드할 수 있습니다.
 */
@ConfigurationProperties(prefix = "recommendation.scoring")
public record RecommendationScoringPolicy(
        @DefaultValue("1.0") double distanceBonus,
        @DefaultValue("0.5") double diversityBonus,
        @DefaultValue("3.0") double teamRecommendationBoost,
        @DefaultValue AiSummary aiSummary,
        @DefaultValue ColdStart coldStart,
        @DefaultValue Freshness freshness,
        @DefaultValue Credibility credibility,
        @DefaultValue Candidate candidate
) {

    /**
     * 테스트 및 기본값 용도의 인스턴스를 생성합니다.
     */
    public static RecommendationScoringPolicy defaults() {
        return new RecommendationScoringPolicy(
                1.0, 0.5, 3.0,
                new AiSummary(0.5, 0.3, -0.2, 4,
                        List.of("단체석", "대형 테이블", "모임", "단체"),
                        List.of("추천", "인기", "맛집", "특별", "유명"),
                        List.of("웨이팅 필수", "예약 필수", "대기 시간")),
                new ColdStart(30, 10, 4.0, 0.3),
                new Freshness(7, 30, 90, 0.3, 0.1, -0.2),
                new Credibility(1.5, 5.0, 3.0, 5.0),
                new Candidate(10, 3, 9)
        );
    }

    public record AiSummary(
            @DefaultValue("0.5") double groupBoost,
            @DefaultValue("0.3") double positiveBoost,
            @DefaultValue("-0.2") double negativePenalty,
            @DefaultValue("4") int groupSizeThreshold,
            @DefaultValue({"단체석", "대형 테이블", "모임", "단체"}) List<String> groupKeywords,
            @DefaultValue({"추천", "인기", "맛집", "특별", "유명"}) List<String> positiveKeywords,
            @DefaultValue({"웨이팅 필수", "예약 필수", "대기 시간"}) List<String> negativeKeywords
    ) {
    }

    public record ColdStart(
            @DefaultValue("30") int daysThreshold,
            @DefaultValue("10") int reviewThreshold,
            @DefaultValue("4.0") double ratingThreshold,
            @DefaultValue("0.3") double boost
    ) {
    }

    public record Freshness(
            @DefaultValue("7") int recentDays,
            @DefaultValue("30") int moderateDays,
            @DefaultValue("90") int staleDays,
            @DefaultValue("0.3") double recentBoost,
            @DefaultValue("0.1") double moderateBoost,
            @DefaultValue("-0.2") double stalePenalty
    ) {
    }

    public record Credibility(
            @DefaultValue("1.5") double blogReviewWeightMultiplier,
            @DefaultValue("5.0") double maxReviewWeight,
            @DefaultValue("3.0") double ratingMin,
            @DefaultValue("5.0") double ratingMax
    ) {
    }

    public record Candidate(
            @DefaultValue("10") int poolSize,
            @DefaultValue("3") int topKSize,
            @DefaultValue("9") int resultSize
    ) {
    }
}
