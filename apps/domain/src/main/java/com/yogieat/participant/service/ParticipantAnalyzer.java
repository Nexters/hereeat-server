package com.yogieat.participant.service;

import com.yogieat.category.domain.value.LargeCategory;
import com.yogieat.common.GeoJson;
import com.yogieat.common.GeoUtils;
import com.yogieat.common.Region;
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
     * 맛집 위치와 region 기준 좌표의 거리를 기준으로 거리 범위를 결정
     *
     * @param restaurantPoint 맛집 좌표
     * @param region         다수결 판단 기준 지역
     * @return 거리 500m 이내면 RANGE_500M, 1km 이내면 RANGE_1KM, 그 외는 ANY
     */
    public DistanceRange determineMajorityDistanceRange(GeoJson.Point restaurantPoint, Region region) {
        if (region == null
                || !GeoUtils.isValidPoint(restaurantPoint)
                || !GeoUtils.isValidPoint(region.getCoordinatesStandard())
        ) {
            return DistanceRange.ANY;
        }

        double distance = GeoUtils.calculateDistanceKm(region.getCoordinatesStandard(), restaurantPoint);
        if (distance <= 0.5) {
            return DistanceRange.RANGE_500M;
        }
        if (distance <= 1.0) {
            return DistanceRange.RANGE_1KM;
        }
        return DistanceRange.ANY;
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

        return participants.stream()
                .collect(Collectors.groupingBy(p -> p.distanceRange().name(), Collectors.summingInt(p -> 1)));
    }
}
