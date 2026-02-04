package com.yogieat.recommend.domain.value;

import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import java.util.Map;

/**
 * 참여자들의 선호도/불호를 카테고리별로 집계한 값 객체
 */
public record CategoryAggregation(
        Map<String, Integer> preferences,
        Map<String, Integer> dislikes
) {
    public CategoryAggregation {
        if (preferences == null || dislikes == null) {
            throw new CustomException(ErrorCode.INVALID_CATEGORY_AGGREGATION);
        }
    }

    public static CategoryAggregation of(Map<String, Integer> preferences, Map<String, Integer> dislikes) {
        return new CategoryAggregation(preferences, dislikes);
    }
}
