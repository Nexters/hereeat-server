package com.yogieat.datasource.db.core.restaurant.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yogieat.restaurant.reservation.domain.RestaurantReservation;
import com.yogieat.restaurant.reservation.domain.value.ReservationProvider;
import com.yogieat.restaurant.reservation.domain.value.ReservationSource;
import com.yogieat.restaurant.reservation.domain.value.ReservationStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RestaurantReservationCoreRepositoryTest {

    @Mock
    private RestaurantReservationJpaRepository jpaRepository;

    @InjectMocks
    private RestaurantReservationCoreRepository coreRepository;

    @Test
    @DisplayName("save는 같은 식당과 provider의 활성 예약이 있으면 상태를 갱신한다")
    void save_ShouldUpdateExistingReservation() {
        RestaurantReservationEntity existingEntity = RestaurantReservationEntity.from(pendingReservation(1L, ReservationProvider.NAVER_BOOKING));
        setEntityId(existingEntity, 11L);

        RestaurantReservation verifiedReservation = new RestaurantReservation(
                null,
                1L,
                ReservationProvider.NAVER_BOOKING,
                ReservationStatus.VERIFIED,
                ReservationSource.ADMIN,
                "https://booking.naver.com/booking/6/bizes/591723",
                "verified",
                "591723",
                "한샘 분당점",
                "경기 성남시 분당구 수내동",
                0.95d,
                LocalDateTime.of(2026, 4, 22, 10, 0),
                LocalDateTime.of(2026, 4, 22, 10, 5),
                null,
                LocalDateTime.of(2026, 4, 22, 10, 5),
                null,
                null
        );

        when(jpaRepository.findByRestaurantIdAndProviderAndDeletedAtIsNull(1L, ReservationProvider.NAVER_BOOKING))
                .thenReturn(Optional.of(existingEntity));
        when(jpaRepository.save(existingEntity)).thenReturn(existingEntity);

        RestaurantReservation saved = coreRepository.save(verifiedReservation);

        assertThat(saved.id()).isEqualTo(11L);
        assertThat(saved.status()).isEqualTo(ReservationStatus.VERIFIED);
        assertThat(existingEntity.getStatus()).isEqualTo(ReservationStatus.VERIFIED);
        assertThat(existingEntity.getSource()).isEqualTo(ReservationSource.ADMIN);
        assertThat(existingEntity.getReservationUrl()).isEqualTo("https://booking.naver.com/booking/6/bizes/591723");
        assertThat(existingEntity.getProviderPlaceKey()).isEqualTo("591723");
        assertThat(existingEntity.getMatchedName()).isEqualTo("한샘 분당점");
        verify(jpaRepository).save(existingEntity);
    }

    @Test
    @DisplayName("save는 활성 예약이 없으면 새 예약을 생성한다")
    void save_ShouldCreateNewReservation_WhenNoActiveReservationExists() {
        RestaurantReservation pendingReservation = pendingReservation(1L, ReservationProvider.CATCHTABLE);

        when(jpaRepository.findByRestaurantIdAndProviderAndDeletedAtIsNull(1L, ReservationProvider.CATCHTABLE))
                .thenReturn(Optional.empty());
        when(jpaRepository.save(org.mockito.ArgumentMatchers.any(RestaurantReservationEntity.class)))
                .thenAnswer(invocation -> {
                    RestaurantReservationEntity entity = invocation.getArgument(0);
                    setEntityId(entity, 21L);
                    return entity;
                });

        RestaurantReservation saved = coreRepository.save(pendingReservation);

        assertThat(saved.id()).isEqualTo(21L);
        assertThat(saved.restaurantId()).isEqualTo(1L);
        assertThat(saved.provider()).isEqualTo(ReservationProvider.CATCHTABLE);
        assertThat(saved.status()).isEqualTo(ReservationStatus.PENDING);
    }

    @Test
    @DisplayName("findActiveByRestaurantId는 활성 예약 목록을 반환한다")
    void findActiveByRestaurantId_ShouldReturnActiveReservations() {
        RestaurantReservationEntity naverEntity = RestaurantReservationEntity.from(pendingReservation(1L, ReservationProvider.NAVER_BOOKING));
        RestaurantReservationEntity catchtableEntity = RestaurantReservationEntity.from(pendingReservation(1L, ReservationProvider.CATCHTABLE));
        setEntityId(naverEntity, 31L);
        setEntityId(catchtableEntity, 32L);

        when(jpaRepository.findAllByRestaurantIdAndDeletedAtIsNullOrderByIdAsc(1L))
                .thenReturn(List.of(naverEntity, catchtableEntity));

        List<RestaurantReservation> reservations = coreRepository.findActiveByRestaurantId(1L);

        assertThat(reservations)
                .extracting(RestaurantReservation::provider)
                .containsExactly(ReservationProvider.NAVER_BOOKING, ReservationProvider.CATCHTABLE);
    }

    @Test
    @DisplayName("softDelete 후에는 같은 provider 예약을 다시 생성할 수 있다")
    void softDeleteAndRecreate_ShouldAllowRecreationForSameProvider() {
        RestaurantReservationEntity existingEntity = RestaurantReservationEntity.from(pendingReservation(1L, ReservationProvider.NAVER_BOOKING));
        setEntityId(existingEntity, 41L);

        when(jpaRepository.findByRestaurantIdAndProviderAndDeletedAtIsNull(1L, ReservationProvider.NAVER_BOOKING))
                .thenReturn(Optional.of(existingEntity))
                .thenReturn(Optional.empty());
        when(jpaRepository.save(org.mockito.ArgumentMatchers.any(RestaurantReservationEntity.class)))
                .thenAnswer(invocation -> {
                    RestaurantReservationEntity entity = invocation.getArgument(0);
                    if (entity != existingEntity) {
                        setEntityId(entity, 42L);
                    }
                    return entity;
                });

        coreRepository.softDeleteByRestaurantIdAndProvider(1L, ReservationProvider.NAVER_BOOKING);
        RestaurantReservation recreated = coreRepository.save(pendingReservation(1L, ReservationProvider.NAVER_BOOKING));

        assertThat(existingEntity.getDeletedAt()).isNotNull();
        assertThat(recreated.id()).isEqualTo(42L);
        assertThat(recreated.provider()).isEqualTo(ReservationProvider.NAVER_BOOKING);
    }

    private RestaurantReservation pendingReservation(Long restaurantId, ReservationProvider provider) {
        return RestaurantReservation.Create.of(
                restaurantId,
                provider,
                ReservationStatus.PENDING,
                ReservationSource.SYSTEM_INIT,
                null,
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

    private void setEntityId(RestaurantReservationEntity entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id, Long.class);
    }
}
