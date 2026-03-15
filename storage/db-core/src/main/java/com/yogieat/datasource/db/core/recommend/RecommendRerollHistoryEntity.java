package com.yogieat.datasource.db.core.recommend;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yogieat.datasource.db.core.common.BaseEntity;
import com.yogieat.recommend.domain.RecommendRerollHistory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.Arrays;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "t_recommend_reroll_history",
        indexes = {
            @Index(name = "idx_recommend_reroll_history_gathering_id", columnList = "gathering_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendRerollHistoryEntity extends BaseEntity {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<RecommendRerollHistory.Result>> REROLL_RESULT_TYPE = new TypeReference<>() {};

    @Column(name = "gathering_id", nullable = false)
    private Long gatheringId;

    @Column(name = "excluded_restaurant_ids", columnDefinition = "TEXT")
    private String excludedRestaurantIds;

    @Column(name = "rerolled_results", columnDefinition = "TEXT")
    private String rerolledResults;

    @Builder(access = AccessLevel.PRIVATE)
    public RecommendRerollHistoryEntity(
            Long gatheringId,
            String excludedRestaurantIds,
            String rerolledResults
    ) {
        this.gatheringId = gatheringId;
        this.excludedRestaurantIds = excludedRestaurantIds;
        this.rerolledResults = rerolledResults;
    }

    public static RecommendRerollHistoryEntity from(RecommendRerollHistory history) {
        return RecommendRerollHistoryEntity.builder()
                .gatheringId(history.gatheringId())
                .excludedRestaurantIds(joinExcludedRestaurantIds(history.excludedRestaurantIds()))
                .rerolledResults(writeRerolledResults(history.rerolledResults()))
                .build();
    }

    public RecommendRerollHistory toDomain() {
        return new RecommendRerollHistory(
                getId(),
                gatheringId,
                parseExcludedRestaurantIds(excludedRestaurantIds),
                parseRerolledResults(rerolledResults),
                getCreatedAt()
        );
    }

    private static String joinExcludedRestaurantIds(List<Long> excludedRestaurantIds) {
        if (excludedRestaurantIds == null || excludedRestaurantIds.isEmpty()) {
            return null;
        }

        return excludedRestaurantIds.stream()
                .map(String::valueOf)
                .reduce((left, right) -> left + "," + right)
                .orElse(null);
    }

    private static List<Long> parseExcludedRestaurantIds(String excludedRestaurantIds) {
        if (excludedRestaurantIds == null || excludedRestaurantIds.isBlank()) {
            return List.of();
        }

        return Arrays.stream(excludedRestaurantIds.split(","))
                .map(String::strip)
                .filter(value -> !value.isBlank())
                .map(Long::valueOf)
                .toList();
    }

    private static String writeRerolledResults(List<RecommendRerollHistory.Result> rerolledResults) {
        if (rerolledResults == null || rerolledResults.isEmpty()) {
            return null;
        }

        try {
            return OBJECT_MAPPER.writeValueAsString(rerolledResults);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize rerolled results", e);
        }
    }

    private static List<RecommendRerollHistory.Result> parseRerolledResults(String rerolledResults) {
        if (rerolledResults == null || rerolledResults.isBlank()) {
            return List.of();
        }

        try {
            return OBJECT_MAPPER.readValue(rerolledResults, REROLL_RESULT_TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize rerolled results", e);
        }
    }
}
