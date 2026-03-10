package com.yogieat.kakao.kakao.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kakao.admin.client")
public record KakaoAdminClientProperties(
        int searchSize,
        int maxRetryAttempts,
        int concurrentPermits,
        long retryBaseDelayMs,
        double retryJitterRate,
        long cacheTtlMs
) {
    private static final int DEFAULT_SEARCH_SIZE = 5;
    private static final int DEFAULT_MAX_RETRY_ATTEMPTS = 3;
    private static final int DEFAULT_CONCURRENT_PERMITS = 6;
    private static final long DEFAULT_RETRY_BASE_DELAY_MS = 180L;
    private static final double DEFAULT_RETRY_JITTER_RATE = 0.2d;
    private static final long DEFAULT_CACHE_TTL_MS = 20_000L;

    public KakaoAdminClientProperties {
        if (searchSize <= 0) {
            searchSize = DEFAULT_SEARCH_SIZE;
        }
        if (maxRetryAttempts <= 0) {
            maxRetryAttempts = DEFAULT_MAX_RETRY_ATTEMPTS;
        }
        if (concurrentPermits <= 0) {
            concurrentPermits = DEFAULT_CONCURRENT_PERMITS;
        }
        if (retryBaseDelayMs <= 0) {
            retryBaseDelayMs = DEFAULT_RETRY_BASE_DELAY_MS;
        }
        if (retryJitterRate < 0d || retryJitterRate > 1d) {
            retryJitterRate = DEFAULT_RETRY_JITTER_RATE;
        }
        if (cacheTtlMs <= 0) {
            cacheTtlMs = DEFAULT_CACHE_TTL_MS;
        }
    }
}
