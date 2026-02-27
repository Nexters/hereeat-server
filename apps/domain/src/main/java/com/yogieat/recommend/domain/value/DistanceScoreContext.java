package com.yogieat.recommend.domain.value;

import com.yogieat.participant.domain.value.DistanceRange;

/**
 * 거리 기반 점수 계산에 필요한 컨텍스트 값 객체입니다.
 */
public record DistanceScoreContext(
        DistanceRange preferredRange,
        double anyRatio,
        double effectiveDistanceBonus
) {
}
