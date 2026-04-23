package com.yogieat.restaurant.reservation.service;

public record RestaurantReservationBackfillResult(
        int processedRestaurants,
        int createdReservations,
        int updatedReservations,
        int skippedProtectedReservations,
        int noMatchReservations
) {
    public static RestaurantReservationBackfillResult empty() {
        return new RestaurantReservationBackfillResult(0, 0, 0, 0, 0);
    }

    public RestaurantReservationBackfillResult plus(RestaurantReservationBackfillResult other) {
        return new RestaurantReservationBackfillResult(
                processedRestaurants + other.processedRestaurants,
                createdReservations + other.createdReservations,
                updatedReservations + other.updatedReservations,
                skippedProtectedReservations + other.skippedProtectedReservations,
                noMatchReservations + other.noMatchReservations
        );
    }
}
