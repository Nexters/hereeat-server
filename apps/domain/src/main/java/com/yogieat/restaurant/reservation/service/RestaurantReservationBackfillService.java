package com.yogieat.restaurant.reservation.service;

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
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class RestaurantReservationBackfillService {

    private final RestaurantRepository restaurantRepository;
    private final RestaurantReservationRepository restaurantReservationRepository;
    private final ReservationSearchClient reservationSearchClient;

    @Transactional
    public RestaurantReservationBackfillResult backfill(List<Long> restaurantIds) {
        if (restaurantIds == null || restaurantIds.isEmpty()) {
            return RestaurantReservationBackfillResult.empty();
        }

        List<Restaurant> restaurants = restaurantRepository.findByIds(restaurantIds);
        RestaurantReservationBackfillResult result = RestaurantReservationBackfillResult.empty();

        for (Restaurant restaurant : restaurants) {
            result = result.plus(backfillLoadedRestaurant(restaurant));
        }

        return result;
    }

    @Transactional
    public RestaurantReservationBackfillResult backfillOneRestaurant(Restaurant restaurant) {
        return backfillLoadedRestaurant(restaurant);
    }

    private RestaurantReservationBackfillResult backfillLoadedRestaurant(Restaurant restaurant) {
        if (restaurant == null || restaurant.id() == null || restaurant.name() == null) {
            return new RestaurantReservationBackfillResult(1, 0, 0, 0, 0);
        }

        Map<ReservationProvider, RestaurantReservation> existingReservations =
                restaurantReservationRepository.findActiveByRestaurantId(restaurant.id()).stream()
                        .collect(Collectors.toMap(RestaurantReservation::provider, Function.identity(), (left, right) -> left));

        int created = 0;
        int updated = 0;
        int skippedProtected = 0;
        int noMatch = 0;

        for (ReservationProvider provider : ReservationProvider.values()) {
            RestaurantReservation existing = existingReservations.get(provider);
            if (isProtected(existing)) {
                skippedProtected++;
                continue;
            }

            if (restaurant.name().isBlank()) {
                noMatch++;
                continue;
            }

            ReservationSearchCandidate candidate = reservationSearchClient.searchBestCandidate(
                            provider,
                            restaurant.name(),
                            restaurant.address()
                    )
                    .orElse(null);

            if (candidate == null) {
                noMatch++;
                continue;
            }

            RestaurantReservation reservation = toAutoMatchedReservation(restaurant.id(), provider, candidate);
            restaurantReservationRepository.save(reservation);

            if (existing == null) {
                created++;
            } else {
                updated++;
            }
        }

        return new RestaurantReservationBackfillResult(1, created, updated, skippedProtected, noMatch);
    }

    private boolean isProtected(RestaurantReservation existing) {
        if (existing == null) {
            return false;
        }
        return existing.source() == ReservationSource.ADMIN || existing.status() == ReservationStatus.VERIFIED;
    }

    private RestaurantReservation toAutoMatchedReservation(
            Long restaurantId,
            ReservationProvider provider,
            ReservationSearchCandidate candidate
    ) {
        LocalDateTime now = LocalDateTime.now();

        return RestaurantReservation.Create.of(
                restaurantId,
                provider,
                ReservationStatus.PENDING,
                ReservationSource.AUTO_MATCH,
                candidate.reservationUrl(),
                buildNote(candidate.searchQuery()),
                candidate.providerPlaceKey(),
                candidate.matchedName(),
                candidate.matchedAddress(),
                candidate.matchScore(),
                now,
                null,
                null,
                now
        );
    }

    private String buildNote(String searchQuery) {
        if (searchQuery == null || searchQuery.isBlank()) {
            return null;
        }
        return "auto-match query: " + searchQuery;
    }
}
