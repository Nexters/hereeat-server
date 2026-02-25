package com.yogieat.recommend.domain.value;

import java.util.Map;
import java.util.Set;

/**
 * 카테고리별 선호/불호 집계와 추천 제외 카테고리 목록을 담는 값 객체입니다.
 */
public record CategoryVoteSummary(
        Map<String, Integer> preferenceVotes,
        Map<String, Integer> dislikeVotes,
        Set<String> excludedCategories
) {
}
