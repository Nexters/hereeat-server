package com.yogieat.restaurant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yogieat.common.Region;
import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import com.yogieat.external.kakao.result.KakaoPlaceDetailData;
import com.yogieat.external.kakao.result.KakaoPlaceDetailFetchResult;
import com.yogieat.restaurant.domain.CreateRestaurant;
import com.yogieat.restaurant.domain.Restaurant;
import com.yogieat.restaurant.fixture.RestaurantFixture;
import com.yogieat.restaurant.result.RestaurantAdminResult;
import com.yogieat.util.LockManager;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class RestaurantServiceTest {

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private RestaurantCommandService restaurantCommandService;

    @Mock
    private RestaurantAdminLookupService restaurantAdminLookupService;

    @Mock
    private RestaurantValidator restaurantValidator;

    @Mock
    private LockManager lockManager;

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

    @Test
    @DisplayName("맛집 생성 중 유니크 충돌이 발생하면 기존 맛집을 중복 응답으로 반환한다")
    void createRestaurant_ShouldReturnDuplicated_WhenSaveConflictsWithExistingExternalId() {
        String externalId = "external";
        RestaurantCommand.Create command = new RestaurantCommand.Create(
                externalId,
                1L,
                Region.fromString("HONGDAE"),
                null
        );
        Restaurant existingRestaurant = RestaurantFixture.sampleSourceRestaurantForAdmin();

        when(lockManager.executeWithLock(anyString(), Mockito.<LockManager.Task<RestaurantAdminResult.Create>>any()))
                .thenAnswer(invocation -> {
                    LockManager.Task<RestaurantAdminResult.Create> task =
                            invocation.<LockManager.Task<RestaurantAdminResult.Create>>getArgument(1);
                    return task.execute();
                });
        when(restaurantRepository.findByExternalId(externalId))
                .thenReturn(Optional.empty(), Optional.of(existingRestaurant));
        when(restaurantAdminLookupService.fetchPlaceDetail(externalId))
                .thenReturn(KakaoPlaceDetailFetchResult.success(sampleDetail(externalId)));
        when(restaurantCommandService.save(any(CreateRestaurant.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate external id"));

        RestaurantAdminResult.Create result = restaurantService.createRestaurant(command);

        assertThat(result).isEqualTo(RestaurantAdminResult.Create.duplicated(existingRestaurant.id()));
        verify(lockManager).executeWithLock(
                eq("restaurant:create:" + externalId),
                Mockito.<LockManager.Task<RestaurantAdminResult.Create>>any()
        );
    }

    private KakaoPlaceDetailData sampleDetail(String externalId) {
        return new KakaoPlaceDetailData(
                externalId,
                "name",
                "address",
                37.0,
                127.0,
                4.0,
                "image",
                List.of(),
                "review",
                10,
                20,
                "menu",
                10000,
                "1",
                "summary-title",
                List.of("summary"),
                "station",
                null,
                null,
                null,
                null,
                null,
                List.of(),
                "02-1234-5678"
        );
    }
}
