package com.yogieat.restaurant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.yogieat.common.Region;
import com.yogieat.restaurant.domain.Restaurant;
import com.yogieat.restaurant.domain.SuggestionRestaurant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RestaurantValidatorTest {

    @Mock
    private RestaurantRepository restaurantRepository;

    @InjectMocks
    private RestaurantValidator restaurantValidator;

    @Test
    @DisplayName("캐시 경로와 DB 경로에서 externalId 중복 검증 결과가 동일하다")
    void duplicateByExternalId_ShouldBeConsistentAcrossCacheAndDb() {
        SuggestionRestaurant suggestion = suggestion("식당A", "주소A");
        when(restaurantRepository.existsByExternalId("k-1")).thenReturn(true);

        RestaurantValidator.ValidationContext context =
                new RestaurantValidator.ValidationContext(new java.util.HashSet<>(List.of("k-1")), new java.util.HashSet<>());

        RestaurantValidator.ValidationResult cacheResult =
                restaurantValidator.duplicateValidateWithCache(context, suggestion, "k-1");
        RestaurantValidator.ValidationResult dbResult =
                restaurantValidator.duplicateValidate(suggestion, "k-1");

        assertThat(cacheResult.isValid()).isFalse();
        assertThat(dbResult.isValid()).isFalse();
        assertThat(cacheResult.reason()).isEqualTo(dbResult.reason());
    }

    @Test
    @DisplayName("캐시 경로와 DB 경로에서 name+address 중복 검증 결과가 동일하다")
    void duplicateByNameAddress_ShouldBeConsistentAcrossCacheAndDb() {
        SuggestionRestaurant suggestion = suggestion("식당B", "주소B");
        when(restaurantRepository.existsByNameAndAddress("식당B", "주소B")).thenReturn(true);

        RestaurantValidator.ValidationContext context =
                new RestaurantValidator.ValidationContext(new java.util.HashSet<>(), new java.util.HashSet<>(List.of("식당B|주소B")));

        RestaurantValidator.ValidationResult cacheResult =
                restaurantValidator.duplicateValidateWithCache(context, suggestion, null);
        RestaurantValidator.ValidationResult dbResult =
                restaurantValidator.duplicateValidate(suggestion, null);

        assertThat(cacheResult.isValid()).isFalse();
        assertThat(dbResult.isValid()).isFalse();
        assertThat(cacheResult.reason()).isEqualTo(dbResult.reason());
    }

    @Test
    @DisplayName("필수값 누락 시 invalid 결과를 반환한다")
    void missingRequiredField_ShouldReturnInvalid() {
        SuggestionRestaurant suggestion = suggestion(" ", "주소C");

        RestaurantValidator.ValidationResult result =
                restaurantValidator.duplicateValidate(suggestion, null);

        assertThat(result.isValid()).isFalse();
        assertThat(result.isDuplicate()).isFalse();
        assertThat(result.reason()).isEqualTo("Restaurant name is required");
    }

    @Test
    @DisplayName("prepareForBatchValidation이 컨텍스트를 만들고 addToCache로 갱신된다")
    void validationContext_ShouldBePreparedAndUpdated() {
        when(restaurantRepository.findByRegion(Region.GANGNAM))
                .thenReturn(List.of(restaurant()));

        RestaurantValidator.ValidationContext context =
                restaurantValidator.prepareForBatchValidation(Region.GANGNAM);

        assertThat(context.containsExternalId("ext-1")).isTrue();
        assertThat(context.containsNameAddress("식당1", "주소1")).isTrue();

        restaurantValidator.addToCache(context, "ext-2", "식당2", "주소2");

        assertThat(context.containsExternalId("ext-2")).isTrue();
        assertThat(context.containsNameAddress("식당2", "주소2")).isTrue();
    }

    private SuggestionRestaurant suggestion(String name, String address) {
        return new SuggestionRestaurant(name, address, 4.2, "한식", "백반", "설명", "리뷰");
    }

    private Restaurant restaurant() {
        return new Restaurant(
                1L,
                "ext-1",
                null,
                "식당1",
                "주소1",
                null,
                null,
                null,
                null,
                null,
                Region.GANGNAM,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
