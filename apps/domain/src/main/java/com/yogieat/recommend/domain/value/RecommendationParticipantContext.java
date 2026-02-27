package com.yogieat.recommend.domain.value;

import java.util.Map;

/**
 * 추천 계산에 필요한 참여자 기반 파생 데이터를 묶은 값 객체입니다.
 */
public record RecommendationParticipantContext(
        Map<String, PreferenceScore> preferenceScoreMap,
        CategoryVoteSummary categoryVoteSummary,
        DistanceScoreContext distanceScoreContext
) {
}
