package com.yogieat.external.reservation.result;

import com.yogieat.restaurant.reservation.domain.value.ReservationProvider;

public record ReservationSearchCandidate(
        ReservationProvider provider,
        String providerPlaceKey,
        String reservationUrl,
        String matchedName,
        String matchedAddress,
        Double matchScore,
        String searchQuery
) {
}
