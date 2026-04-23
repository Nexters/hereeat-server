package com.yogieat.external.reservation;

import com.yogieat.external.reservation.result.ReservationSearchCandidate;
import com.yogieat.restaurant.reservation.domain.value.ReservationProvider;
import java.util.Optional;

public interface ReservationSearchClient {

    Optional<ReservationSearchCandidate> searchBestCandidate(
            ReservationProvider provider,
            String restaurantName,
            String address
    );
}
