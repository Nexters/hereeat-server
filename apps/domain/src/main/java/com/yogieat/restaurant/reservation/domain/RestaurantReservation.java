package com.yogieat.restaurant.reservation.domain;

import com.yogieat.restaurant.reservation.domain.value.ReservationProvider;
import com.yogieat.restaurant.reservation.domain.value.ReservationSource;
import com.yogieat.restaurant.reservation.domain.value.ReservationStatus;
import java.time.LocalDateTime;

public record RestaurantReservation(
        Long id,
        Long restaurantId,
        ReservationProvider provider,
        ReservationStatus status,
        ReservationSource source,
        String reservationUrl,
        String note,
        String providerPlaceKey,
        String matchedName,
        String matchedAddress,
        Double matchScore,
        LocalDateTime matchedAt,
        LocalDateTime verifiedAt,
        LocalDateTime rejectedAt,
        LocalDateTime lastCheckedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record Create(
            Long restaurantId,
            ReservationProvider provider,
            ReservationStatus status,
            ReservationSource source,
            String reservationUrl,
            String note,
            String providerPlaceKey,
            String matchedName,
            String matchedAddress,
            Double matchScore,
            LocalDateTime matchedAt,
            LocalDateTime verifiedAt,
            LocalDateTime rejectedAt,
            LocalDateTime lastCheckedAt
    ) {
        public static RestaurantReservation of(
                Long restaurantId,
                ReservationProvider provider,
                ReservationStatus status,
                ReservationSource source,
                String reservationUrl,
                String note,
                String providerPlaceKey,
                String matchedName,
                String matchedAddress,
                Double matchScore,
                LocalDateTime matchedAt,
                LocalDateTime verifiedAt,
                LocalDateTime rejectedAt,
                LocalDateTime lastCheckedAt
        ) {
            return new RestaurantReservation(
                    null,
                    restaurantId,
                    provider,
                    status,
                    source,
                    reservationUrl,
                    note,
                    providerPlaceKey,
                    matchedName,
                    matchedAddress,
                    matchScore,
                    matchedAt,
                    verifiedAt,
                    rejectedAt,
                    lastCheckedAt,
                    null,
                    null
            );
        }
    }
}
