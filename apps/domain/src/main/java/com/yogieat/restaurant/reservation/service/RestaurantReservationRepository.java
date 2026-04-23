package com.yogieat.restaurant.reservation.service;

import com.yogieat.restaurant.reservation.domain.RestaurantReservation;
import com.yogieat.restaurant.reservation.domain.value.ReservationProvider;
import java.util.List;
import java.util.Optional;

public interface RestaurantReservationRepository {
    RestaurantReservation save(RestaurantReservation reservation);
    List<RestaurantReservation> findActiveByRestaurantId(Long restaurantId);
    Optional<RestaurantReservation> findActiveByRestaurantIdAndProvider(Long restaurantId, ReservationProvider provider);
    void softDeleteByRestaurantIdAndProvider(Long restaurantId, ReservationProvider provider);
}
