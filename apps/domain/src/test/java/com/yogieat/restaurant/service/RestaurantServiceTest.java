package com.yogieat.restaurant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import com.yogieat.restaurant.domain.Restaurant;
import com.yogieat.restaurant.fixture.RestaurantFixture;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RestaurantServiceTest {

    @Mock
    private RestaurantRepository restaurantRepository;

    @InjectMocks
    private RestaurantService restaurantService;

    @Test
    @DisplayName("id로 단건 조회 시 맛집이 없으면 예외가 발생한다")
    void getBy_ShouldThrowException_WhenRestaurantNotFound() {
        when(restaurantRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> restaurantService.getBy(1L))
                .isInstanceOf(CustomException.class)
                .hasMessage(ErrorCode.RESTAURANT_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("updateForAdmin는 패치 적용 후 갱신된 데이터를 반환한다")
    void updateBy_ShouldApplyPatchAndReturnUpdatedRestaurant() {
        Long id = 1L;
        Restaurant source = RestaurantFixture.sampleSourceRestaurantForAdmin();
        Restaurant updated = RestaurantFixture.sampleUpdatedRestaurantForAdmin(source);
        RestaurantCommand.Patch command = RestaurantFixture.samplePatchForAdmin();

        when(restaurantRepository.applyAdminPatch(anyLong(), any(RestaurantCommand.Patch.class))).thenReturn(updated);

        Restaurant result = restaurantService.updateBy(id, command);
        ArgumentCaptor<RestaurantCommand.Patch> commandCaptor = ArgumentCaptor.forClass(RestaurantCommand.Patch.class);

        assertThat(result).isEqualTo(updated);
        verify(restaurantRepository).applyAdminPatch(eq(id), commandCaptor.capture());
        assertThat(commandCaptor.getValue()).isEqualTo(command);
    }
}
