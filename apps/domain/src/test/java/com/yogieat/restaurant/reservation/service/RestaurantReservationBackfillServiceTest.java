package com.yogieat.restaurant.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yogieat.common.Region;
import com.yogieat.external.reservation.ReservationSearchClient;
import com.yogieat.external.reservation.result.ReservationSearchCandidate;
import com.yogieat.restaurant.domain.Restaurant;
import com.yogieat.restaurant.reservation.domain.RestaurantReservation;
import com.yogieat.restaurant.reservation.domain.value.ReservationProvider;
import com.yogieat.restaurant.reservation.domain.value.ReservationSource;
import com.yogieat.restaurant.reservation.domain.value.ReservationStatus;
import com.yogieat.restaurant.service.RestaurantRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RestaurantReservationBackfillServiceTest {

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private RestaurantReservationRepository restaurantReservationRepository;

    @Mock
    private ReservationSearchClient reservationSearchClient;

    @InjectMocks
    private RestaurantReservationBackfillService backfillService;

    @Test
    @DisplayName("자동매칭 후보가 있으면 AUTO_MATCH PENDING 예약을 저장한다")
    void backfill_ShouldSaveAutoMatchedReservation() {
        Restaurant restaurant = sampleRestaurant();
        ReservationSearchCandidate naverCandidate = new ReservationSearchCandidate(
                ReservationProvider.NAVER_BOOKING,
                "591723",
                "https://booking.naver.com/booking/6/bizes/591723",
                "한샘 분당점",
                "경기 성남시 분당구 수내동",
                0.92d,
                "site:booking.naver.com/booking/ \"한샘 분당점\""
        );

        when(restaurantRepository.findByIds(List.of(1L))).thenReturn(List.of(restaurant));
        when(restaurantReservationRepository.findActiveByRestaurantId(1L)).thenReturn(List.of());
        when(reservationSearchClient.searchBestCandidate(
                ReservationProvider.NAVER_BOOKING,
                "한샘 분당점",
                "경기 성남시 분당구 수내동 21-2"
        )).thenReturn(Optional.of(naverCandidate));
        when(reservationSearchClient.searchBestCandidate(
                ReservationProvider.CATCHTABLE,
                "한샘 분당점",
                "경기 성남시 분당구 수내동 21-2"
        )).thenReturn(Optional.empty());
        when(restaurantReservationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RestaurantReservationBackfillResult result = backfillService.backfill(List.of(1L));

        ArgumentCaptor<RestaurantReservation> captor = ArgumentCaptor.forClass(RestaurantReservation.class);
        verify(restaurantReservationRepository).save(captor.capture());
        RestaurantReservation saved = captor.getValue();

        assertThat(result.processedRestaurants()).isEqualTo(1);
        assertThat(result.createdReservations()).isEqualTo(1);
        assertThat(result.noMatchReservations()).isEqualTo(1);
        assertThat(saved.provider()).isEqualTo(ReservationProvider.NAVER_BOOKING);
        assertThat(saved.source()).isEqualTo(ReservationSource.AUTO_MATCH);
        assertThat(saved.status()).isEqualTo(ReservationStatus.PENDING);
        assertThat(saved.providerPlaceKey()).isEqualTo("591723");
        assertThat(saved.matchScore()).isEqualTo(0.92d);
    }

    @Test
    @DisplayName("주소가 없어도 식당 이름 기준으로 자동매칭을 시도한다")
    void backfill_ShouldSearchByRestaurantNameWhenAddressIsMissing() {
        Restaurant restaurant = sampleRestaurantWithAddress(null);
        ReservationSearchCandidate naverCandidate = new ReservationSearchCandidate(
                ReservationProvider.NAVER_BOOKING,
                "591723",
                "https://booking.naver.com/booking/6/bizes/591723",
                "한샘 분당점",
                null,
                0.8d,
                "한샘 분당점 booking.naver.com/booking"
        );

        when(restaurantRepository.findByIds(List.of(1L))).thenReturn(List.of(restaurant));
        when(restaurantReservationRepository.findActiveByRestaurantId(1L)).thenReturn(List.of());
        when(reservationSearchClient.searchBestCandidate(
                ReservationProvider.NAVER_BOOKING,
                "한샘 분당점",
                null
        )).thenReturn(Optional.of(naverCandidate));
        when(reservationSearchClient.searchBestCandidate(
                ReservationProvider.CATCHTABLE,
                "한샘 분당점",
                null
        )).thenReturn(Optional.empty());
        when(restaurantReservationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RestaurantReservationBackfillResult result = backfillService.backfill(List.of(1L));

        ArgumentCaptor<RestaurantReservation> captor = ArgumentCaptor.forClass(RestaurantReservation.class);
        verify(restaurantReservationRepository).save(captor.capture());

        assertThat(result.createdReservations()).isEqualTo(1);
        assertThat(result.noMatchReservations()).isEqualTo(1);
        assertThat(captor.getValue().reservationUrl()).isEqualTo("https://booking.naver.com/booking/6/bizes/591723");
    }

    @Test
    @DisplayName("ADMIN 소스 예약은 자동매칭이 덮어쓰지 않는다")
    void backfill_ShouldNotOverrideAdminReservation() {
        Restaurant restaurant = sampleRestaurant();
        RestaurantReservation adminReservation = new RestaurantReservation(
                10L,
                1L,
                ReservationProvider.NAVER_BOOKING,
                ReservationStatus.REJECTED,
                ReservationSource.ADMIN,
                "https://booking.naver.com/booking/6/bizes/111111",
                null,
                "111111",
                "운영 확인",
                "운영 확인 주소",
                1.0d,
                LocalDateTime.now(),
                null,
                LocalDateTime.now(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        when(restaurantRepository.findByIds(List.of(1L))).thenReturn(List.of(restaurant));
        when(restaurantReservationRepository.findActiveByRestaurantId(1L)).thenReturn(List.of(adminReservation));
        when(reservationSearchClient.searchBestCandidate(
                ReservationProvider.CATCHTABLE,
                "한샘 분당점",
                "경기 성남시 분당구 수내동 21-2"
        )).thenReturn(Optional.empty());

        RestaurantReservationBackfillResult result = backfillService.backfill(List.of(1L));

        verify(restaurantReservationRepository, never()).save(any());
        assertThat(result.skippedProtectedReservations()).isEqualTo(1);
    }

    private Restaurant sampleRestaurant() {
        return sampleRestaurantWithAddress("경기 성남시 분당구 수내동 21-2");
    }

    private Restaurant sampleRestaurantWithAddress(String address) {
        return new Restaurant(
                1L,
                "kakao-1",
                1L,
                "한샘 분당점",
                address,
                4.3d,
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
                null,
                LocalDateTime.now(),
                LocalDateTime.now(),
                List.of()
        );
    }
}
