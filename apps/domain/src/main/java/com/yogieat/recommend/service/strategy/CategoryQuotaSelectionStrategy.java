package com.yogieat.recommend.service.strategy;

import com.yogieat.recommend.domain.value.CategoryScoredRestaurant;
import com.yogieat.recommend.domain.value.ScoredRestaurant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 카테고리 선호 비율 기반 Top-K 선정 전략입니다.
 */
@Component
public class CategoryQuotaSelectionStrategy implements RecommendationSelectionStrategy {

    @Override
    public List<ScoredRestaurant> selectTopRestaurants(
            List<CategoryScoredRestaurant> scoredByCategory,
            Map<String, Integer> preferenceVotes,
            int topKSize,
            int candidatePoolSize
    ) {
        if (scoredByCategory.isEmpty() || topKSize <= 0) {
            return List.of();
        }

        Map<String, List<ScoredRestaurant>> categoryBuckets =
                buildCategoryBuckets(scoredByCategory, candidatePoolSize);

        List<String> slotSequence = buildCategorySlotSequence(
                categoryBuckets,
                preferenceVotes,
                topKSize
        );

        List<ScoredRestaurant> selected = new ArrayList<>(topKSize);
        Set<Long> selectedRestaurantIds = new HashSet<>();

        for (String category : slotSequence) {
            if (selected.size() >= topKSize) {
                break;
            }
            ScoredRestaurant candidate = nextUnselectedCandidate(
                    categoryBuckets.get(category),
                    selectedRestaurantIds
            );
            if (candidate != null) {
                selected.add(candidate);
                selectedRestaurantIds.add(candidate.restaurant().id());
            }
        }

        if (selected.size() >= topKSize) {
            return selected;
        }

        // 슬롯 배분으로 부족한 경우, 전체 후보에서 점수순으로 보강
        List<ScoredRestaurant> remainingCandidates = scoredByCategory.stream()
                .map(CategoryScoredRestaurant::scoredRestaurant)
                .filter(candidate -> !selectedRestaurantIds.contains(candidate.restaurant().id()))
                .sorted(Comparator.comparingDouble(ScoredRestaurant::totalScore).reversed())
                .toList();

        for (ScoredRestaurant remaining : remainingCandidates) {
            if (selected.size() >= topKSize) {
                break;
            }
            selected.add(remaining);
        }

        return selected;
    }

    private Map<String, List<ScoredRestaurant>> buildCategoryBuckets(
            List<CategoryScoredRestaurant> scoredByCategory,
            int candidatePoolSize) {
        Map<String, List<ScoredRestaurant>> buckets = new HashMap<>();

        for (CategoryScoredRestaurant item : scoredByCategory) {
            buckets.computeIfAbsent(item.categoryName(), key -> new ArrayList<>())
                    .add(item.scoredRestaurant());
        }

        for (Map.Entry<String, List<ScoredRestaurant>> entry : buckets.entrySet()) {
            List<ScoredRestaurant> sorted = entry.getValue().stream()
                    .sorted(Comparator.comparingDouble(ScoredRestaurant::totalScore).reversed())
                    .limit(candidatePoolSize)
                    .collect(Collectors.toCollection(ArrayList::new));
            entry.setValue(sorted);
        }

        return buckets;
    }

    private List<String> buildCategorySlotSequence(
            Map<String, List<ScoredRestaurant>> categoryBuckets,
            Map<String, Integer> preferenceVotes,
            int topKSize) {
        List<String> quotaCategories = preferenceVotes.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .filter(entry -> categoryBuckets.containsKey(entry.getKey()))
                .filter(entry -> !categoryBuckets.get(entry.getKey()).isEmpty())
                .map(Map.Entry::getKey)
                .toList();

        if (quotaCategories.isEmpty()) {
            return List.of();
        }

        int totalVotes = quotaCategories.stream()
                .mapToInt(category -> preferenceVotes.getOrDefault(category, 0))
                .sum();

        if (totalVotes <= 0) {
            return List.of();
        }

        Map<String, Integer> slotCounts = new HashMap<>();
        Map<String, Double> remainders = new HashMap<>();
        int allocatedSlots = 0;

        for (String category : quotaCategories) {
            int votes = preferenceVotes.getOrDefault(category, 0);
            int maxSlotsByCandidates = categoryBuckets.get(category).size();

            double rawQuota = ((double) votes / totalVotes) * topKSize;
            int baseSlots = Math.min((int) Math.floor(rawQuota), maxSlotsByCandidates);
            slotCounts.put(category, baseSlots);
            remainders.put(category, rawQuota - Math.floor(rawQuota));
            allocatedSlots += baseSlots;
        }

        while (allocatedSlots < topKSize) {
            String nextCategory = quotaCategories.stream()
                    .filter(category -> slotCounts.getOrDefault(category, 0) < categoryBuckets.get(category).size())
                    .sorted(Comparator
                            .comparingDouble((String category) -> remainders.getOrDefault(category, 0.0))
                            .reversed()
                            .thenComparing(
                                    Comparator.comparingInt((String category) -> preferenceVotes.getOrDefault(category, 0))
                                            .reversed()
                            )
                            .thenComparing(
                                    (String category) -> bestCategoryScore(category, categoryBuckets),
                                    Comparator.reverseOrder()
                            )
                            .thenComparing(Comparator.naturalOrder()))
                    .findFirst()
                    .orElse(null);

            if (nextCategory == null) {
                break;
            }

            slotCounts.merge(nextCategory, 1, Integer::sum);
            allocatedSlots++;
        }

        List<String> slotOrder = quotaCategories.stream()
                .sorted(Comparator
                        .comparingInt((String category) -> preferenceVotes.getOrDefault(category, 0))
                        .reversed()
                        .thenComparing(
                                (String category) -> bestCategoryScore(category, categoryBuckets),
                                Comparator.reverseOrder()
                        )
                        .thenComparing(Comparator.naturalOrder()))
                .toList();

        List<String> slotSequence = new ArrayList<>(topKSize);
        for (String category : slotOrder) {
            int count = slotCounts.getOrDefault(category, 0);
            for (int i = 0; i < count && slotSequence.size() < topKSize; i++) {
                slotSequence.add(category);
            }
        }
        return slotSequence;
    }

    private double bestCategoryScore(
            String category,
            Map<String, List<ScoredRestaurant>> categoryBuckets) {
        List<ScoredRestaurant> candidates = categoryBuckets.get(category);
        if (candidates == null || candidates.isEmpty()) {
            return Double.NEGATIVE_INFINITY;
        }
        return candidates.getFirst().totalScore();
    }

    private ScoredRestaurant nextUnselectedCandidate(
            List<ScoredRestaurant> candidates,
            Set<Long> selectedRestaurantIds) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        for (ScoredRestaurant candidate : candidates) {
            if (!selectedRestaurantIds.contains(candidate.restaurant().id())) {
                return candidate;
            }
        }
        return null;
    }
}
