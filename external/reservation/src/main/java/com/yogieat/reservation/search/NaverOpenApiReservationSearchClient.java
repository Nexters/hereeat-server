package com.yogieat.reservation.search;

import com.yogieat.external.reservation.ReservationSearchClient;
import com.yogieat.external.reservation.result.ReservationSearchCandidate;
import com.yogieat.reservation.search.config.ReservationSearchProperties;
import com.yogieat.restaurant.reservation.domain.value.ReservationProvider;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@ConditionalOnProperty(name = "reservation.search.provider", havingValue = "naver-open-api", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class NaverOpenApiReservationSearchClient implements ReservationSearchClient {

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

    private final ReservationSearchProperties properties;
    private final RestClient reservationSearchRestClient;
    private final DuckDuckGoSearchScorer scorer = new DuckDuckGoSearchScorer();
    private final AtomicBoolean missingCredentialsLogged = new AtomicBoolean(false);

    @Override
    public Optional<ReservationSearchCandidate> searchBestCandidate(
            ReservationProvider provider,
            String restaurantName,
            String address
    ) {
        if (!properties.enabled() || restaurantName == null || restaurantName.isBlank()) {
            return Optional.empty();
        }
        if (!properties.hasNaverCredentials()) {
            logMissingCredentials();
            return Optional.empty();
        }

        List<ReservationSearchCandidate> candidates = new ArrayList<>();
        for (String query : buildQueries(provider, restaurantName, address)) {
            NaverWebSearchResponse response = fetchSearchResult(query);
            if (response == null || response.items() == null || response.items().isEmpty()) {
                continue;
            }

            for (NaverWebSearchItem item : response.items()) {
                String url = cleanHtml(item.link());
                if (!supportsProviderUrl(provider, url)) {
                    continue;
                }

                String title = cleanHtml(item.title());
                String description = cleanHtml(item.description());
                double score = scorer.score(restaurantName, address, title, description, url);
                if (score < properties.minMatchScore()) {
                    continue;
                }

                candidates.add(new ReservationSearchCandidate(
                        provider,
                        extractProviderPlaceKey(provider, url),
                        url,
                        emptyToNull(title),
                        scorer.extractMatchedAddress(address, description),
                        score,
                        query
                ));
            }
        }

        return candidates.stream()
                .max(Comparator.comparing(ReservationSearchCandidate::matchScore));
    }

    private NaverWebSearchResponse fetchSearchResult(String query) {
        try {
            return reservationSearchRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/search/webkr.json")
                            .queryParam("query", query)
                            .queryParam("display", properties.resolvedNaverDisplay())
                            .queryParam("start", 1)
                            .build())
                    .retrieve()
                    .body(NaverWebSearchResponse.class);
        } catch (HttpClientErrorException exception) {
            logClientError(query, exception);
            return null;
        } catch (RestClientException exception) {
            log.warn("Naver reservation search failed: query={}, message={}", query, exception.getMessage());
            log.debug("Naver reservation search failure detail", exception);
            return null;
        }
    }

    private List<String> buildQueries(ReservationProvider provider, String restaurantName, String address) {
        String areaToken = scorer.extractAreaToken(address);
        Set<String> queries = new LinkedHashSet<>();

        for (String site : providerSites(provider)) {
            queries.add(restaurantName.strip() + " " + site);
            if (areaToken != null && !areaToken.isBlank()) {
                queries.add(restaurantName.strip() + " " + areaToken + " " + site);
            }
        }

        return new ArrayList<>(queries).stream()
                .limit(properties.resolvedMaxQueriesPerProvider())
                .toList();
    }

    private List<String> providerSites(ReservationProvider provider) {
        return switch (provider) {
            case NAVER_BOOKING -> List.of(
                    "booking.naver.com/booking",
                    "waiting.booking.naver.com/bizes"
            );
            case CATCHTABLE -> List.of(
                    "app.catchtable.co.kr/ct/shop",
                    "catchtable.co.kr/ct/shop"
            );
        };
    }

    private boolean supportsProviderUrl(ReservationProvider provider, String url) {
        if (url == null || url.isBlank()) {
            return false;
        }

        return switch (provider) {
            case NAVER_BOOKING -> url.contains("booking.naver.com/booking/") || url.contains("waiting.booking.naver.com/bizes/");
            case CATCHTABLE -> url.contains("app.catchtable.co.kr/ct/shop/") || url.contains("catchtable.co.kr/ct/shop/");
        };
    }

    private String extractProviderPlaceKey(ReservationProvider provider, String url) {
        if (url == null || url.isBlank()) {
            return null;
        }

        return switch (provider) {
            case NAVER_BOOKING -> scorer.extractPattern(url, "/bizes/([0-9]+)");
            case CATCHTABLE -> scorer.extractPattern(url, "/ct/shop/([^?/#]+)");
        };
    }

    private String cleanHtml(String raw) {
        if (raw == null) {
            return null;
        }
        String withoutTags = HTML_TAG_PATTERN.matcher(raw).replaceAll(" ");
        return withoutTags
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#x27;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replaceAll("\\s+", " ")
                .strip();
    }

    private String emptyToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private void logMissingCredentials() {
        if (missingCredentialsLogged.compareAndSet(false, true)) {
            log.warn("Naver reservation search is enabled but NAVER_SEARCH_CLIENT_ID or NAVER_SEARCH_CLIENT_SECRET is missing");
        }
    }

    private void logClientError(String query, HttpClientErrorException exception) {
        HttpStatusCode statusCode = exception.getStatusCode();
        log.warn("Naver reservation search failed: status={}, query={}, message={}",
                statusCode.value(),
                query,
                exception.getMessage());
        log.debug("Naver reservation search failure detail", exception);
    }

    private record NaverWebSearchResponse(List<NaverWebSearchItem> items) {
    }

    private record NaverWebSearchItem(String title, String link, String description) {
    }
}
