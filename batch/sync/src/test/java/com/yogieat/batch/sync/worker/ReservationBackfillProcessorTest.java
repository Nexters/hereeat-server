package com.yogieat.batch.sync.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.yogieat.batch.sync.config.ReservationBackfillProperties;
import com.yogieat.common.Region;
import com.yogieat.restaurant.domain.Restaurant;
import com.yogieat.restaurant.reservation.service.RestaurantReservationBackfillResult;
import com.yogieat.restaurant.reservation.service.RestaurantReservationBackfillService;
import com.yogieat.restaurant.service.RestaurantRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReservationBackfillProcessorTest {

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private RestaurantReservationBackfillService restaurantReservationBackfillService;

    @Test
    @DisplayName("예약 백필은 활성 식당을 chunk 단위로 처리하고 결과를 합산한다")
    void process_ShouldBackfillRestaurantsByChunk() {
        ReservationBackfillProperties properties = new ReservationBackfillProperties(
                false,
                false,
                2,
                2,
                0L,
                null,
                null
        );
        try (ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor()) {
            ReservationBackfillProcessor processor = new ReservationBackfillProcessor(
                    properties,
                    restaurantRepository,
                    restaurantReservationBackfillService,
                    executorService
            );

            Restaurant firstRestaurant = sampleRestaurant(1L, "첫번째 식당");
            Restaurant secondRestaurant = sampleRestaurant(2L, "두번째 식당");
            Restaurant thirdRestaurant = sampleRestaurant(3L, "세번째 식당");

            when(restaurantRepository.countActiveRestaurants()).thenReturn(3L);
            when(restaurantRepository.findActiveRestaurantIdsAfter(null, 2)).thenReturn(List.of(1L, 2L));
            when(restaurantRepository.findActiveRestaurantIdsAfter(2L, 1)).thenReturn(List.of(3L));
            when(restaurantRepository.findByIds(List.of(1L, 2L))).thenReturn(List.of(firstRestaurant, secondRestaurant));
            when(restaurantRepository.findByIds(List.of(3L))).thenReturn(List.of(thirdRestaurant));
            when(restaurantReservationBackfillService.backfillOneRestaurant(firstRestaurant))
                    .thenReturn(new RestaurantReservationBackfillResult(1, 1, 0, 0, 1));
            when(restaurantReservationBackfillService.backfillOneRestaurant(secondRestaurant))
                    .thenReturn(new RestaurantReservationBackfillResult(1, 0, 0, 0, 1));
            when(restaurantReservationBackfillService.backfillOneRestaurant(thirdRestaurant))
                    .thenReturn(new RestaurantReservationBackfillResult(1, 0, 1, 0, 0));

            RestaurantReservationBackfillResult result = processor.process("test");

            assertThat(result.processedRestaurants()).isEqualTo(3);
            assertThat(result.createdReservations()).isEqualTo(1);
            assertThat(result.updatedReservations()).isEqualTo(1);
            assertThat(result.noMatchReservations()).isEqualTo(2);
        }
    }

    private Restaurant sampleRestaurant(Long id, String name) {
        return new Restaurant(
                id,
                "kakao-" + id,
                1L,
                name,
                "서울 마포구 테스트로 " + id,
                4.3d,
                null,
                null,
                null,
                null,
                Region.HONGDAE,
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
