package com.yogieat.domain.recommend.domain.value;

import java.util.Map;

/**
 * 참여자들의 선호도/불호를 카테고리별로 집계한 값 객체
 */
public record CategoryAggregation(
        Map<String, Integer> preferences,
        Map<String, Integer> dislikes
) {
    public CategoryAggregation {
        if (preferences == null) {
            throw new IllegalArgumentException("Preferences cannot be null");
        }
        if (dislikes == null) {
            throw new IllegalArgumentException("Dislikes cannot be null");
        }
    }

    public static CategoryAggregation of(Map<String, Integer> preferences, Map<String, Integer> dislikes) {
        return new CategoryAggregation(preferences, dislikes);
    }
}
