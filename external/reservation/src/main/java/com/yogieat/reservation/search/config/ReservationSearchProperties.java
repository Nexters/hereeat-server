package com.yogieat.reservation.search.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "reservation.search")
public record ReservationSearchProperties(
        boolean enabled,
        String provider,
        String baseUrl,
        String userAgent,
        double minMatchScore,
        int maxResultsPerQuery,
        int maxQueriesPerProvider,
        long blockedCooldownMinutes,
        long requestDelayMs,
        String naverBaseUrl,
        String naverClientId,
        String naverClientSecret,
        int naverDisplay,
        String browserWorkerBaseUrl,
        long readTimeoutSeconds
) {
    private static final String DUCKDUCKGO_PROVIDER = "duckduckgo";
    private static final String NAVER_OPEN_API_PROVIDER = "naver-open-api";
    private static final String BROWSER_WORKER_PROVIDER = "browser-worker";

    public String resolvedProvider() {
        return provider == null || provider.isBlank() ? NAVER_OPEN_API_PROVIDER : provider;
    }

    public boolean isDuckDuckGoProvider() {
        return DUCKDUCKGO_PROVIDER.equalsIgnoreCase(resolvedProvider());
    }

    public boolean isNaverOpenApiProvider() {
        return NAVER_OPEN_API_PROVIDER.equalsIgnoreCase(resolvedProvider());
    }

    public boolean isBrowserWorkerProvider() {
        return BROWSER_WORKER_PROVIDER.equalsIgnoreCase(resolvedProvider());
    }

    public String resolvedBaseUrl() {
        if (isNaverOpenApiProvider()) {
            return naverBaseUrl == null || naverBaseUrl.isBlank() ? "https://openapi.naver.com" : naverBaseUrl;
        }
        if (isBrowserWorkerProvider()) {
            return browserWorkerBaseUrl == null || browserWorkerBaseUrl.isBlank()
                    ? "http://yogieat-reservation-browser-worker:8090"
                    : browserWorkerBaseUrl;
        }
        return baseUrl == null || baseUrl.isBlank() ? "https://html.duckduckgo.com" : baseUrl;
    }

    public String resolvedUserAgent() {
        return userAgent == null || userAgent.isBlank() ? "yogieat-reservation-search/1.0" : userAgent;
    }

    public int resolvedMaxResultsPerQuery() {
        return Math.max(1, maxResultsPerQuery);
    }

    public int resolvedMaxQueriesPerProvider() {
        return Math.max(1, maxQueriesPerProvider);
    }

    public Duration resolvedBlockedCooldown() {
        return Duration.ofMinutes(Math.max(1, blockedCooldownMinutes));
    }

    public long resolvedRequestDelayMs() {
        return Math.max(0L, requestDelayMs);
    }

    public boolean hasNaverCredentials() {
        return naverClientId != null && !naverClientId.isBlank()
                && naverClientSecret != null && !naverClientSecret.isBlank();
    }

    public int resolvedNaverDisplay() {
        return Math.max(1, Math.min(100, naverDisplay));
    }

    public Duration resolvedReadTimeout() {
        if (readTimeoutSeconds > 0) {
            return Duration.ofSeconds(readTimeoutSeconds);
        }
        if (isBrowserWorkerProvider()) {
            return Duration.ofSeconds(45);
        }
        return Duration.ofSeconds(8);
    }
}
