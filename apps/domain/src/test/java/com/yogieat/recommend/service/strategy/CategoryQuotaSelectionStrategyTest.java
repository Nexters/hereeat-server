package com.yogieat.recommend.service.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import com.yogieat.common.Region;
import com.yogieat.gathering.domain.value.TimeSlot;
import com.yogieat.recommend.domain.value.CategoryScoredRestaurant;
import com.yogieat.recommend.domain.value.ScoredRestaurant;
import com.yogieat.restaurant.domain.Restaurant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CategoryQuotaSelectionStrategyTest {

    private final CategoryQuotaSelectionStrategy strategy = new CategoryQuotaSelectionStrategy();

    @Test
    @DisplayName("동률 카테고리 슬롯 배분 시 같은 카테고리에 연속 쏠리지 않는다")
    void shouldDistributeSlotsAcrossTiedCategoriesBeforeDuplicating() {
        // given
        List<CategoryScoredRestaurant> scored = List.of(
                item("한식", 101L, 100.0),
                item("한식", 102L, 99.0),
                item("한식", 103L, 98.0),
                item("중식", 201L, 97.0),
                item("양식", 301L, 96.0),
                item("아시안", 401L, 95.0)
        );

        Map<String, Integer> preferenceVotes = Map.of(
                "한식", 2,
                "중식", 2,
                "양식", 2,
                "아시안", 2
        );

        // when
        List<ScoredRestaurant> top3 = strategy.selectTopRestaurants(
                scored,
                preferenceVotes,
                3,
                10
        );

        // then
        assertThat(top3).hasSize(3);
        assertThat(top3.stream().map(sr -> sr.restaurant().categoryId()).distinct().count()).isEqualTo(3L);
        assertThat(top3.stream().map(sr -> sr.restaurant().categoryId()).toList())
                .containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("선호 카테고리 후보가 부족하면 비선호 카테고리로 보강해 TopK를 채운다")
    void shouldBackfillFromNonPreferredWhenPreferredCandidatesAreInsufficient() {
        // given
        List<CategoryScoredRestaurant> scored = List.of(
                item("한식", 101L, 100.0),
                item("한식", 102L, 99.0),
                item("중식", 201L, 120.0),
                item("아시안", 301L, 119.0)
        );

        Map<String, Integer> preferenceVotes = Map.of(
                "한식", 4,
                "양식", 2
        );

        // when
        List<ScoredRestaurant> top3 = strategy.selectTopRestaurants(
                scored,
                preferenceVotes,
                3,
                10
        );

        // then
        assertThat(top3).hasSize(3);
        assertThat(top3.stream().map(sr -> sr.restaurant().categoryId()).toList())
                .containsExactly(1L, 1L, 2L);
    }

    private CategoryScoredRestaurant item(String categoryName, Long restaurantId, double score) {
        Restaurant restaurant = new Restaurant(
                restaurantId,
                "ext-" + restaurantId,
                categoryIdOf(categoryName),
                categoryName + "-" + restaurantId,
                "address",
                4.5,
                null,
                null,
                null,
                null,
                Region.fromString("GANGNAM"),
                null,
                10,
                0,
                null,
                null,
                null,
                null,
                null,
                TimeSlot.BOTH,
                null,
                null,
                null,
                null,
                null,
                null,
                true
        );

        return new CategoryScoredRestaurant(
                categoryName,
                new ScoredRestaurant(restaurant, score, 50.0, "")
        );
    }

    private Long categoryIdOf(String categoryName) {
        return switch (categoryName) {
            case "한식" -> 1L;
            case "중식" -> 2L;
            case "양식" -> 3L;
            case "아시안" -> 4L;
            default -> 99L;
        };
    }
}
