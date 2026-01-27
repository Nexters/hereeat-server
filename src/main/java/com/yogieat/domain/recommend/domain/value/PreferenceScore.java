package com.yogieat.domain.recommend.domain.value;

import com.yogieat.global.error.CustomException;
import com.yogieat.global.error.ErrorCode;

/**
 * 카테고리별 선호도 점수를 미리 집계한 값 객체
 * 참여자들의 선호도를 한 번에 집계하여 Restaurant 점수 계산 시 재사용
 */
public record PreferenceScore(
    double totalPreferenceScore,
    int dislikeCount
) {
    public PreferenceScore {
        if (totalPreferenceScore < 0 && dislikeCount == 0) {
            throw new CustomException(ErrorCode.INVALID_PREFERENCE_SCORE);
        }
    }

    public static PreferenceScore empty() {
        return new PreferenceScore(0.0, 0);
    }

    public PreferenceScore addPreference(int rank) {
        double scoreToAdd = switch (rank) {
            case 1 -> 3.0;
            case 2 -> 2.0;
            case 3 -> 1.0;
            default -> 0.0;
        };
        return new PreferenceScore(totalPreferenceScore + scoreToAdd, dislikeCount);
    }

    public PreferenceScore addDislike() {
        return new PreferenceScore(totalPreferenceScore, dislikeCount + 1);
    }

    public double calculateFinalScore() {
        return totalPreferenceScore - (dislikeCount * 2.0);
    }
}
