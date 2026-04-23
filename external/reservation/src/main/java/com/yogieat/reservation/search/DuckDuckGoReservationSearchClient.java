package com.yogieat.reservation.search;

import com.yogieat.external.reservation.ReservationSearchClient;
import com.yogieat.external.reservation.result.ReservationSearchCandidate;
import com.yogieat.reservation.search.config.ReservationSearchProperties;
import com.yogieat.restaurant.reservation.domain.value.ReservationProvider;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
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
@ConditionalOnProperty(name = "reservation.search.provider", havingValue = "duckduckgo")
@RequiredArgsConstructor
@Slf4j
public class DuckDuckGoReservationSearchClient implements ReservationSearchClient {

    private static final Pattern RESULT_LINK_PATTERN = Pattern.compile(
            "(?s)<a[^>]*class=\"[^\"]*result__a[^\"]*\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>"
    );
    private static final Pattern RESULT_SNIPPET_PATTERN = Pattern.compile(
            "(?s)<a[^>]*class=\"[^\"]*result__snippet[^\"]*\"[^>]*>(.*?)</a>|<div[^>]*class=\"[^\"]*result__snippet[^\"]*\"[^>]*>(.*?)</div>"
    );
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

    private final ReservationSearchProperties properties;
    private final RestClient reservationSearchRestClient;
    private final DuckDuckGoSearchScorer scorer = new DuckDuckGoSearchScorer();
    private final AtomicLong blockedUntilEpochMs = new AtomicLong(0L);

    @Override
    public Optional<ReservationSearchCandidate> searchBestCandidate(
            ReservationProvider provider,
            String restaurantName,
            String address
    ) {
        if (!properties.enabled() || restaurantName == null || restaurantName.isBlank() || isSearchTemporarilyBlocked()) {
            return Optional.empty();
        }

        List<String> queries = buildQueries(provider, restaurantName, address);
        List<ReservationSearchCandidate> candidates = new ArrayList<>();

        for (String query : queries) {
            if (isSearchTemporarilyBlocked()) {
                break;
            }

            String html = fetchSearchHtml(query);
            if (html == null || html.isBlank()) {
                continue;
            }

            List<SearchHit> hits = parseHits(html);
            for (SearchHit hit : hits) {
                if (!supportsProviderUrl(provider, hit.url())) {
                    continue;
                }

                double score = scorer.score(restaurantName, address, hit.title(), hit.snippet(), hit.url());
                if (score < properties.minMatchScore()) {
                    continue;
                }

                candidates.add(new ReservationSearchCandidate(
                        provider,
                        extractProviderPlaceKey(provider, hit.url()),
                        hit.url(),
                        emptyToNull(hit.title()),
                        scorer.extractMatchedAddress(address, hit.snippet()),
                        score,
                        query
                ));
            }

            delayNextRequest();
        }

        return candidates.stream()
                .max(Comparator.comparing(ReservationSearchCandidate::matchScore));
    }

    private String fetchSearchHtml(String query) {
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            return reservationSearchRestClient.get()
                    .uri("/html/?q={query}", encodedQuery)
                    .retrieve()
                    .body(String.class);
        } catch (HttpClientErrorException exception) {
            handleClientError(query, exception);
            return null;
        } catch (RestClientException exception) {
            log.warn("Reservation search failed: query={}, message={}", query, exception.getMessage());
            log.debug("Reservation search failure detail", exception);
            return null;
        }
    }

    private List<SearchHit> parseHits(String html) {
        Matcher linkMatcher = RESULT_LINK_PATTERN.matcher(html);
        List<SearchHit> hits = new ArrayList<>();
        int snippetSearchStart = 0;

        while (linkMatcher.find()) {
            String rawHref = linkMatcher.group(1);
            String url = decodeDuckDuckGoRedirect(rawHref);
            if (url == null) {
                continue;
            }

            String title = cleanHtml(linkMatcher.group(2));
            String snippet = extractNearestSnippet(html, snippetSearchStart);
            snippetSearchStart = linkMatcher.end();
            hits.add(new SearchHit(url, title, snippet));
        }

        return hits.stream()
                .limit(properties.resolvedMaxResultsPerQuery())
                .toList();
    }

    private String extractNearestSnippet(String html, int startIndex) {
        Matcher snippetMatcher = RESULT_SNIPPET_PATTERN.matcher(html);
        if (snippetMatcher.find(startIndex)) {
            String snippet = snippetMatcher.group(1) != null ? snippetMatcher.group(1) : snippetMatcher.group(2);
            return cleanHtml(snippet);
        }
        return null;
    }

    private List<String> buildQueries(ReservationProvider provider, String restaurantName, String address) {
        String areaToken = scorer.extractAreaToken(address);
        Set<String> queries = new LinkedHashSet<>();

        for (String site : providerSites(provider)) {
            queries.add("site:" + site + " \"" + restaurantName.strip() + "\"");
            if (areaToken != null && !areaToken.isBlank()) {
                queries.add("site:" + site + " \"" + restaurantName.strip() + "\" \"" + areaToken + "\"");
            }
        }

        return new ArrayList<>(queries).stream()
                .limit(properties.resolvedMaxQueriesPerProvider())
                .toList();
    }

    private boolean isSearchTemporarilyBlocked() {
        long blockedUntil = blockedUntilEpochMs.get();
        if (blockedUntil == 0L) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now < blockedUntil) {
            return true;
        }

        blockedUntilEpochMs.compareAndSet(blockedUntil, 0L);
        return false;
    }

    private void handleClientError(String query, HttpClientErrorException exception) {
        HttpStatusCode statusCode = exception.getStatusCode();
        if (statusCode.value() == 403 || statusCode.value() == 429) {
            blockSearch(query, statusCode, exception);
            return;
        }

        log.warn("Reservation search failed: status={}, query={}, message={}",
                statusCode.value(), query, exception.getMessage());
        log.debug("Reservation search failure detail", exception);
    }

    private void blockSearch(String query, HttpStatusCode statusCode, HttpClientErrorException exception) {
        long now = System.currentTimeMillis();
        long blockedUntil = now + properties.resolvedBlockedCooldown().toMillis();
        long previousBlockedUntil = blockedUntilEpochMs.getAndUpdate(current -> Math.max(current, blockedUntil));

        if (now >= previousBlockedUntil) {
            log.warn("Reservation search temporarily disabled: status={}, cooldownMinutes={}, query={}",
                    statusCode.value(),
                    properties.resolvedBlockedCooldown().toMinutes(),
                    query);
        } else {
            log.debug("Reservation search remains temporarily disabled: status={}, query={}",
                    statusCode.value(),
                    query);
        }
        log.debug("Reservation search throttling detail", exception);
    }

    private void delayNextRequest() {
        long delayMs = properties.resolvedRequestDelayMs();
        if (delayMs <= 0L) {
            return;
        }

        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
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

    private String decodeDuckDuckGoRedirect(String rawHref) {
        if (rawHref == null || rawHref.isBlank()) {
            return null;
        }

        String href = rawHref.replace("&amp;", "&");
        if (href.startsWith("//")) {
            href = "https:" + href;
        }

        String uddgPrefix = "uddg=";
        int uddgIndex = href.indexOf(uddgPrefix);
        if (uddgIndex >= 0) {
            String encoded = href.substring(uddgIndex + uddgPrefix.length());
            int nextAmpersand = encoded.indexOf('&');
            if (nextAmpersand >= 0) {
                encoded = encoded.substring(0, nextAmpersand);
            }
            return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
        }

        return href.startsWith("http") ? href : null;
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

    private record SearchHit(String url, String title, String snippet) {
    }
}
