package com.yogieat.participant.domain.value;

import lombok.Getter;

@Getter
public enum DistanceRange {
    RANGE_500M(0.5),
    RANGE_1KM(1.0),
    ANY(null),
    ;

    private final Double distance;

    DistanceRange(Double distance) {
        this.distance = distance;
    }

    /**
     * Double 값을 DistanceRange로 변환
     * null이면 ANY 반환
     */
    public static DistanceRange fromDistance(Double distance) {
        if (distance == null) {
            return ANY;
        }

        for (DistanceRange range : values()) {
            if (range == ANY) {
                continue;
            }
            if (range.distance.equals(distance)) {
                return range;
            }
        }

        // 일치하는 값이 없으면 ANY 반환
        return ANY;
    }
}
