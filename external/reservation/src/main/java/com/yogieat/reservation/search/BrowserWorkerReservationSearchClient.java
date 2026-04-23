package com.yogieat.reservation.search;

import com.yogieat.external.reservation.ReservationSearchClient;
import com.yogieat.external.reservation.result.ReservationSearchCandidate;
import com.yogieat.reservation.search.config.ReservationSearchProperties;
import com.yogieat.restaurant.reservation.domain.value.ReservationProvider;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@ConditionalOnProperty(name = "reservation.search.provider", havingValue = "browser-worker")
@RequiredArgsConstructor
@Slf4j
public class BrowserWorkerReservationSearchClient implements ReservationSearchClient {

    private final ReservationSearchProperties properties;
    private final RestClient reservationSearchRestClient;

    @Override
    public Optional<ReservationSearchCandidate> searchBestCandidate(
            ReservationProvider provider,
            String restaurantName,
            String address
    ) {
        if (!properties.enabled() || restaurantName == null || restaurantName.isBlank()) {
            return Optional.empty();
        }

        try {
            BrowserWorkerSearchResponse response = reservationSearchRestClient.post()
                    .uri("/reservation-candidates/search")
                    .body(new BrowserWorkerSearchRequest(null, restaurantName, address, provider.name()))
                    .retrieve()
                    .body(BrowserWorkerSearchResponse.class);

            if (response == null || response.reservationUrl() == null || response.reservationUrl().isBlank()) {
                logBrowserWorkerFailure(provider, restaurantName, response);
                return Optional.empty();
            }

            return Optional.of(new ReservationSearchCandidate(
                    provider,
                    response.providerPlaceKey(),
                    response.reservationUrl(),
                    response.matchedName(),
                    response.matchedAddress(),
                    response.matchScore(),
                    "browser-worker"
            ));
        } catch (RestClientException exception) {
            log.warn("Reservation browser worker request failed: provider={}, restaurantName={}, message={}",
                    provider,
                    restaurantName,
                    exception.getMessage());
            log.debug("Reservation browser worker failure detail", exception);
            return Optional.empty();
        }
    }

    private void logBrowserWorkerFailure(
            ReservationProvider provider,
            String restaurantName,
            BrowserWorkerSearchResponse response
    ) {
        if (response == null || response.failureReason() == null || response.failureReason().isBlank()) {
            return;
        }

        log.info("Reservation browser worker returned no candidate: provider={}, restaurantName={}, failureReason={}",
                provider,
                restaurantName,
                response.failureReason());
    }

    private record BrowserWorkerSearchRequest(
            Long restaurantId,
            String restaurantName,
            String address,
            String provider
    ) {
    }

    private record BrowserWorkerSearchResponse(
            String reservationUrl,
            String providerPlaceKey,
            String matchedName,
            String matchedAddress,
            Double matchScore,
            String failureReason
    ) {
    }
}
