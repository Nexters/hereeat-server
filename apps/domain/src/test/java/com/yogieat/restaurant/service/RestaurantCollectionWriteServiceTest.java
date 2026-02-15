package com.yogieat.restaurant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yogieat.category.domain.value.LargeCategory;
import com.yogieat.category.service.CategoryService;
import com.yogieat.common.Region;
import com.yogieat.restaurant.domain.SuggestionRestaurant;
import java.util.HashSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RestaurantCollectionWriteServiceTest {

    @Mock
    private CategoryService categoryService;

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private RestaurantValidator restaurantValidator;

    @InjectMocks
    private RestaurantCollectionWriteService writeService;

    @Test
    @DisplayName("유효한 데이터면 레스토랑을 저장한다")
    void persistRestaurant_ShouldSave_WhenValidationPasses() {
        SuggestionRestaurant suggestion = suggestion();
        RestaurantEnrichedData data = enriched("ext-1");
        RestaurantValidator.ValidationContext context =
                new RestaurantValidator.ValidationContext(new HashSet<>(), new HashSet<>());

        when(categoryService.findOrCreateCategory(LargeCategory.KOREAN, suggestion.mediumCategory())).thenReturn(10L);
        when(restaurantValidator.duplicateValidateWithCache(context, suggestion, "ext-1"))
                .thenReturn(RestaurantValidator.ValidationResult.valid());

        boolean saved = writeService.persistRestaurant(suggestion, Region.GANGNAM, LargeCategory.KOREAN, suggestion.mediumCategory(), data, context);

        assertThat(saved).isTrue();
        verify(restaurantRepository).save(any());
    }

    @Test
    @DisplayName("검증 실패면 저장하지 않고 false를 반환한다")
    void persistRestaurant_ShouldReturnFalse_WhenValidationFails() {
        SuggestionRestaurant suggestion = suggestion();
        RestaurantEnrichedData data = enriched("ext-1");
        RestaurantValidator.ValidationContext context =
                new RestaurantValidator.ValidationContext(new HashSet<>(), new HashSet<>());

        when(categoryService.findOrCreateCategory(LargeCategory.KOREAN, suggestion.mediumCategory())).thenReturn(10L);
        when(restaurantValidator.duplicateValidateWithCache(context, suggestion, "ext-1"))
                .thenReturn(RestaurantValidator.ValidationResult.duplicate("dup"));

        boolean saved = writeService.persistRestaurant(suggestion, Region.GANGNAM, LargeCategory.KOREAN, suggestion.mediumCategory(), data, context);

        assertThat(saved).isFalse();
        verify(restaurantRepository, never()).save(any());
    }

    private SuggestionRestaurant suggestion() {
        return new SuggestionRestaurant(
                "가게",
                "주소",
                4.2,
                "한식",
                "백반",
                "설명",
                "리뷰"
        );
    }

    private RestaurantEnrichedData enriched(String externalId) {
        RestaurantEnrichedData data = RestaurantEnrichedData.fromSuggestion(suggestion());
        data.externalId = externalId;
        data.placeName = "가게";
        return data;
    }
}
