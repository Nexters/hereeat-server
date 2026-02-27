package com.yogieat.restaurant.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.yogieat.category.domain.value.LargeCategory;
import org.junit.jupiter.api.Test;

class RestaurantCategoryResolverTest {

    @Test
    void resolveFromKakao_whenApiCategoryExists_returnsApiCategory() {
        RestaurantCategoryResolver.CategoryResolution result = RestaurantCategoryResolver.resolveFromKakao(
                LargeCategory.CHINESE,
                "광동요리",
                "중식",
                "광동요리"
        );

        assertThat(result).isNotNull();
        assertThat(result.largeCategory()).isEqualTo(LargeCategory.CHINESE);
        assertThat(result.mediumCategory()).isEqualTo("광동요리");
    }

    @Test
    void resolveFromKakao_whenNameKeywordMatches_infersLargeCategory() {
        RestaurantCategoryResolver.CategoryResolution result = RestaurantCategoryResolver.resolveFromKakao(
                null,
                null,
                "술집",
                "와인바"
        );

        assertThat(result).isNotNull();
        assertThat(result.largeCategory()).isEqualTo(LargeCategory.WESTERN);
        assertThat(result.mediumCategory()).isEqualTo("와인바");
    }

    @Test
    void resolveForCollection_whenKakaoCategoryMissing_usesRequestedCategoryFallback() {
        RestaurantCategoryResolver.CategoryResolution result = RestaurantCategoryResolver.resolveForCollection(
                "중식",
                "양식",
                "기타",
                null,
                null,
                null,
                null
        );

        assertThat(result).isNotNull();
        assertThat(result.largeCategory()).isEqualTo(LargeCategory.CHINESE);
        assertThat(result.mediumCategory()).isEqualTo("기타");
    }

    @Test
    void resolveFromKakao_whenNoSignal_returnsNull() {
        RestaurantCategoryResolver.CategoryResolution result = RestaurantCategoryResolver.resolveFromKakao(
                null,
                null,
                null,
                null
        );

        assertThat(result).isNull();
    }
}
