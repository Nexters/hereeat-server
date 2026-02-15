package com.yogieat.recommend.domain.value;

import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;

/**
 * 카테고리별 선호도 점수를 미리 집계한 값 객체
 * 참여자들의 선호도를 한 번에 집계하여 Restaurant 점수 계산 시 재사용
 */
public record PreferenceScore(
    double totalPreferenceScore,
    int dislikeCount,
    int preferenceCount  // 선호자 수 (신규)
) {
    public PreferenceScore {
        if (totalPreferenceScore < 0 && dislikeCount == 0 && preferenceCount == 0) {
            throw new CustomException(ErrorCode.INVALID_PREFERENCE_SCORE);
        }
    }

    public static PreferenceScore empty() {
        return new PreferenceScore(0.0, 0, 0);
    }

    public PreferenceScore addPreference(int rank) {
        double scoreToAdd = switch (rank) {
            case 1 -> 3.0;
            case 2 -> 2.0;
            case 3 -> 1.0;
            default -> 0.0;
        };
        return new PreferenceScore(totalPreferenceScore + scoreToAdd, dislikeCount, preferenceCount + 1);
    }

    public PreferenceScore addDislike() {
        return new PreferenceScore(totalPreferenceScore, dislikeCount + 1, preferenceCount);
    }

    /**
     * 기존 점수 계산 (하위 호환)
     */
    public double calculateFinalScore() {
        return totalPreferenceScore - (dislikeCount * 2.0);
    }

    /**
     * 순수 선호수 (선호자 - 불호자)
     */
    public int getNetPreference() {
        return preferenceCount - dislikeCount;
    }

    /**
     * 순수 선호수 기준 가산점 계산
     * - 순수 선호 > 0: +1.0점 × 순수 선호수
     * - 순수 선호 = 0: -0.5점 (중립)
     * - 순수 선호 < 0: -2.0점 × |순수 선호수| (강한 페널티)
     */
    public double calculateNetPreferenceBonus() {
        int netPreference = getNetPreference();
        if (netPreference > 0) {
            return netPreference * 1.0;
        } else if (netPreference == 0) {
            return -0.5;
        } else {
            return netPreference * 2.0;  // 음수 × 양수 = 음수 (페널티)
        }
    }
}
