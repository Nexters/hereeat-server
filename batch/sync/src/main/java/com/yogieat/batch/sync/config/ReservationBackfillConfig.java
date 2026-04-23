package com.yogieat.batch.sync.config;

import com.yogieat.external.reservation.ReservationSearchClient;
import com.yogieat.restaurant.reservation.service.RestaurantReservationBackfillService;
import com.yogieat.restaurant.reservation.service.RestaurantReservationRepository;
import com.yogieat.restaurant.service.RestaurantRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReservationBackfillConfig {

    @Bean
    public RestaurantReservationBackfillService restaurantReservationBackfillService(
            RestaurantRepository restaurantRepository,
            RestaurantReservationRepository restaurantReservationRepository,
            ReservationSearchClient reservationSearchClient
    ) {
        return new RestaurantReservationBackfillService(
                restaurantRepository,
                restaurantReservationRepository,
                reservationSearchClient
        );
    }
}
