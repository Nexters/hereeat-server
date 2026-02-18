package com.yogieat.participant.service;

import com.yogieat.category.domain.value.LargeCategory;
import com.yogieat.participant.domain.Participant;
import com.yogieat.participant.domain.value.DistanceRange;
import com.yogieat.recommend.domain.value.CategoryAggregation;
import com.yogieat.util.StringUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 참여자 데이터 분석을 담당하는 공통 컴포넌트
 * - 다수결 거리 범위 결정
 * - 카테고리별 선호도/불호 집계
 */
@Component
@Slf4j
public class ParticipantAnalyzer {

    /**
     * 참여자들의 DistanceRange를 다수결로 결정
     *
     * @param participants 참여자 목록
     * @return 다수결로 선택된 DistanceRange
     */
    public DistanceRange determineMajorityDistanceRange(List<Participant> participants) {
        if (participants == null || participants.isEmpty()) {
            return DistanceRange.ANY;
        }

        Map<DistanceRange, Long> rangeCount = participants.stream()
                .collect(Collectors.groupingBy(Participant::distanceRange, Collectors.counting()));

        return rangeCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(DistanceRange.ANY);
    }

    /**
     * 참여자들의 선호도와 불호를 카테고리별로 집계
     *
     * @param participants 참여자 목록
     * @return 카테고리별 선호도/불호 집계 결과
     */
    public CategoryAggregation aggregateCategoryPreferences(List<Participant> participants) {
        if (participants == null || participants.isEmpty()) {
            return CategoryAggregation.of(Map.of(), Map.of());
        }

        Map<String, Integer> preferences = new HashMap<>();
        Map<String, Integer> dislikes = new HashMap<>();

        for (Participant participant : participants) {
            // 선호도 집계
            List<String> prefList = StringUtils.splitByComma(participant.preferences());
            for (String pref : prefList) {
                // displayName 또는 enum name을 LargeCategory로 변환
                LargeCategory category = LargeCategory.fromString(pref);
                if (category != null) {
                    preferences.merge(category.name(), 1, Integer::sum);
                }
            }

            // 불호 집계
            List<String> dislikeList = StringUtils.splitByComma(participant.dislikes());
            for (String dislike : dislikeList) {
                // displayName 또는 enum name을 LargeCategory로 변환
                LargeCategory category = LargeCategory.fromString(dislike);
                if (category != null) {
                    dislikes.merge(category.name(), 1, Integer::sum);
                }
            }
        }

        return CategoryAggregation.of(preferences, dislikes);
    }

    /**
     * 참여자들의 DistanceRange를 집계
     *
     * @param participants 참여자 목록
     * @return DistanceRange별 참여자 수 집계 결과
     */
    public Map<String, Integer> aggregateDistanceRanges(List<Participant> participants) {
        if (participants == null || participants.isEmpty()) {
            return Map.of();
        }

        Map<String, Integer> distances = new HashMap<>();
        for (Participant participant : participants) {
            String rangeName = participant.distanceRange().name();
            distances.merge(rangeName, 1, Integer::sum);
        }

        return distances;
    }
}
