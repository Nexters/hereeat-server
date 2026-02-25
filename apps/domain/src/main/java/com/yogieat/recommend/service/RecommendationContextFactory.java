package com.yogieat.recommend.service;

import com.yogieat.category.domain.value.LargeCategory;
import com.yogieat.participant.domain.Participant;
import com.yogieat.participant.domain.value.DistanceRange;
import com.yogieat.recommend.domain.value.CategoryVoteSummary;
import com.yogieat.recommend.domain.value.DistanceScoreContext;
import com.yogieat.recommend.domain.value.PreferenceScore;
import com.yogieat.recommend.domain.value.RecommendationParticipantContext;
import com.yogieat.util.StringUtils;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 참여자 입력 데이터를 추천 계산용 컨텍스트로 변환합니다.
 */
@Component
public class RecommendationContextFactory {

    public RecommendationParticipantContext create(
            List<Participant> participants,
            RecommendationScoringPolicy scoringPolicy
    ) {
        Map<String, PreferenceScore> preferenceScoreMap = aggregatePreferenceScores(participants);
        CategoryVoteSummary categoryVoteSummary = aggregateCategoryVotes(participants);
        DistanceScoreContext distanceScoreContext = buildDistanceScoreContext(participants, scoringPolicy);

        return new RecommendationParticipantContext(
                preferenceScoreMap,
                categoryVoteSummary,
                distanceScoreContext
        );
    }

    /**
     * 참여자들의 선호도와 불호를 카테고리별로 미리 집계
     * O(P × 3) 시간에 모든 선호도 점수를 계산하여 O(R × P × 3) 반복을 제거
     * enum name ("KOREAN") 또는 displayName ("한식") 모두 처리하며, displayName으로 정규화합니다.
     */
    private Map<String, PreferenceScore> aggregatePreferenceScores(List<Participant> participants) {
        Map<String, PreferenceScore> scoreMap = new HashMap<>();

        for (Participant participant : participants) {
            // 선호도 집계
            List<String> preferences = StringUtils.splitByComma(participant.preferences());
            for (int i = 0; i < preferences.size(); i++) {
                String pref = preferences.get(i);
                if (!isNeutralCategorySelection(pref)) {
                    String normalizedPref = normalizeToDisplayName(pref);
                    if (normalizedPref != null) {
                        PreferenceScore current = scoreMap.getOrDefault(normalizedPref, PreferenceScore.empty());
                        scoreMap.put(normalizedPref, current.addPreference(i + 1)); // rank는 1-based
                    }
                }
            }

            // 불호 집계
            List<String> dislikes = StringUtils.splitByComma(participant.dislikes());
            for (String dislike : dislikes) {
                if (!isNeutralCategorySelection(dislike)) {
                    String normalizedDislike = normalizeToDisplayName(dislike);
                    if (normalizedDislike != null) {
                        PreferenceScore current = scoreMap.getOrDefault(normalizedDislike, PreferenceScore.empty());
                        scoreMap.put(normalizedDislike, current.addDislike());
                    }
                }
            }
        }

        return scoreMap;
    }

    /**
     * 카테고리별 선호/불호 투표수를 집계하고 제외 카테고리를 산출합니다.
     */
    private CategoryVoteSummary aggregateCategoryVotes(List<Participant> participants) {
        Map<String, Integer> preferenceVotes = new HashMap<>();
        Map<String, Integer> dislikeVotes = new HashMap<>();

        for (Participant participant : participants) {
            // 인원수 기준 집계를 위해 참여자 내 중복 카테고리는 1회만 카운트
            Set<String> participantPreferenceSet = new HashSet<>();
            Set<String> participantDislikeSet = new HashSet<>();

            for (String pref : StringUtils.splitByComma(participant.preferences())) {
                if (isNeutralCategorySelection(pref)) {
                    continue;
                }
                String normalized = normalizeToDisplayName(pref);
                if (normalized != null) {
                    participantPreferenceSet.add(normalized);
                }
            }

            for (String dislike : StringUtils.splitByComma(participant.dislikes())) {
                if (isNeutralCategorySelection(dislike)) {
                    continue;
                }
                String normalized = normalizeToDisplayName(dislike);
                if (normalized != null) {
                    participantDislikeSet.add(normalized);
                }
            }

            participantPreferenceSet.forEach(category -> preferenceVotes.merge(category, 1, Integer::sum));
            participantDislikeSet.forEach(category -> dislikeVotes.merge(category, 1, Integer::sum));
        }

        Set<String> allCategories = new HashSet<>(preferenceVotes.keySet());
        allCategories.addAll(dislikeVotes.keySet());

        Set<String> excludedCategories = new HashSet<>();
        for (String category : allCategories) {
            int prefCount = preferenceVotes.getOrDefault(category, 0);
            int dislikeCount = dislikeVotes.getOrDefault(category, 0);

            // 확정 정책
            // 1) 불호가 선호보다 많은 카테고리 제외
            // 2) 선호 0 + 불호 존재 카테고리 제외
            if (dislikeCount > prefCount || (prefCount == 0 && dislikeCount > 0)) {
                excludedCategories.add(category);
            }
        }

        return new CategoryVoteSummary(
                Map.copyOf(preferenceVotes),
                Map.copyOf(dislikeVotes),
                Set.copyOf(excludedCategories)
        );
    }

    /**
     * 거리 선호 컨텍스트를 계산합니다.
     * - RANGE_500M vs RANGE_1KM 다수결 (동률 시 RANGE_500M)
     * - ANY 비중에 따라 거리 보너스를 선형 축소
     */
    private DistanceScoreContext buildDistanceScoreContext(
            List<Participant> participants,
            RecommendationScoringPolicy scoringPolicy
    ) {
        if (participants == null || participants.isEmpty()) {
            return new DistanceScoreContext(DistanceRange.ANY, 0.0, 0.0);
        }

        long range500mCount = 0L;
        long range1kmCount = 0L;
        long anyCount = 0L;

        for (Participant participant : participants) {
            DistanceRange range = participant.distanceRange() != null ? participant.distanceRange() : DistanceRange.ANY;
            switch (range) {
                case RANGE_500M -> range500mCount++;
                case RANGE_1KM -> range1kmCount++;
                case ANY -> anyCount++;
            }
        }

        DistanceRange preferredRange = determinePreferredDistanceRange(range500mCount, range1kmCount);
        double anyRatio = (double) anyCount / participants.size();
        double effectiveDistanceBonus = scoringPolicy.distanceBonus() * (1.0 - anyRatio);

        if (preferredRange == DistanceRange.ANY || effectiveDistanceBonus <= 0.0) {
            return new DistanceScoreContext(preferredRange, anyRatio, 0.0);
        }

        return new DistanceScoreContext(preferredRange, anyRatio, effectiveDistanceBonus);
    }

    private DistanceRange determinePreferredDistanceRange(long range500mCount, long range1kmCount) {
        if (range500mCount == 0 && range1kmCount == 0) {
            return DistanceRange.ANY;
        }
        if (range500mCount >= range1kmCount) {
            return DistanceRange.RANGE_500M;
        }
        return DistanceRange.RANGE_1KM;
    }

    private boolean isNeutralCategorySelection(String value) {
        return "상관없음".equals(value) || "ANY".equals(value);
    }

    /**
     * 카테고리 값을 displayName으로 정규화합니다.
     * enum name ("KOREAN", "CHINESE" 등) 또는 displayName ("한식", "중식" 등) 모두 처리합니다.
     */
    private String normalizeToDisplayName(String categoryValue) {
        // 1. 이미 displayName이면 그대로 반환
        LargeCategory byDisplayName = LargeCategory.fromDisplayName(categoryValue);
        if (byDisplayName != null) {
            return categoryValue;
        }

        // 2. enum name이면 displayName으로 변환
        LargeCategory byEnumName = LargeCategory.fromString(categoryValue);
        if (byEnumName != null) {
            return byEnumName.getDisplayName();
        }

        // 3. 알 수 없는 값
        return null;
    }
}
