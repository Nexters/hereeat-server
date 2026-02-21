package com.yogieat.restaurant.service;

import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import com.yogieat.external.kakao.KakaoPlaceClient;
import com.yogieat.external.kakao.KakaoPlaceDetailClient;
import com.yogieat.external.kakao.result.KaKaoPlaceDocumentResult;
import com.yogieat.external.kakao.result.KakaoPlaceDetailFetchResult;
import com.yogieat.external.kakao.result.KakaoPlaceDetailFetchStatus;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

@Service
public class RestaurantAdminLookupService {

    private static final Logger log = LoggerFactory.getLogger(RestaurantAdminLookupService.class);

    private static final String SEARCH_CACHE_PREFIX = "admin-search:";
    private static final String DETAIL_CACHE_PREFIX = "admin-detail:";
    private static final int KAKAO_ADMIN_SEARCH_SIZE_DEFAULT = 5;
    private static final int KAKAO_ADMIN_MAX_RETRY_ATTEMPTS_DEFAULT = 3;
    private static final int KAKAO_ADMIN_CONCURRENT_PERMITS_DEFAULT = 6;
    private static final long KAKAO_ADMIN_RETRY_BASE_DELAY_MS_DEFAULT = 180L;
    private static final double KAKAO_ADMIN_RETRY_JITTER_RATE_DEFAULT = 0.2d;
    private static final long KAKAO_ADMIN_API_TIMEOUT_MS = 1_200L;
    private static final long KAKAO_ADMIN_CACHE_TTL_MS_DEFAULT = 20_000L;

    private final ObjectProvider<KakaoPlaceClient> kakaoPlaceClientProvider;
    private final ObjectProvider<KakaoPlaceDetailClient> kakaoPlaceDetailClientProvider;
    private final Semaphore kakaoApiSemaphore;
    private final int kakaoAdminMaxRetryAttempts;
    private final long kakaoAdminRetryBaseDelayMs;
    private final double kakaoAdminRetryJitterRate;
    private final long kakaoAdminCacheTtlMs;
    private final ConcurrentHashMap<String, CompletableFuture<List<KaKaoPlaceDocumentResult>>> searchInFlight =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<KakaoPlaceDetailFetchResult>> detailInFlight =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CachedValue<List<KaKaoPlaceDocumentResult>>> searchCache =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CachedValue<KakaoPlaceDetailFetchResult>> detailCache =
            new ConcurrentHashMap<>();

    public RestaurantAdminLookupService(
            ObjectProvider<KakaoPlaceClient> kakaoPlaceClientProvider,
            ObjectProvider<KakaoPlaceDetailClient> kakaoPlaceDetailClientProvider
    ) {
        this.kakaoPlaceClientProvider = kakaoPlaceClientProvider;
        this.kakaoPlaceDetailClientProvider = kakaoPlaceDetailClientProvider;
        this.kakaoApiSemaphore = new Semaphore(Math.max(1, KAKAO_ADMIN_CONCURRENT_PERMITS_DEFAULT));
        this.kakaoAdminMaxRetryAttempts = KAKAO_ADMIN_MAX_RETRY_ATTEMPTS_DEFAULT;
        this.kakaoAdminRetryBaseDelayMs = KAKAO_ADMIN_RETRY_BASE_DELAY_MS_DEFAULT;
        this.kakaoAdminRetryJitterRate = KAKAO_ADMIN_RETRY_JITTER_RATE_DEFAULT;
        this.kakaoAdminCacheTtlMs = KAKAO_ADMIN_CACHE_TTL_MS_DEFAULT;
    }

    public List<KaKaoPlaceDocumentResult> searchByKeyword(String keyword, int requestedSize) {
        String normalizedKeyword = keyword == null ? "" : keyword.strip();
        if (normalizedKeyword.isBlank()) {
            return List.of();
        }

        int limit = Math.max(1, Math.min(requestedSize, KAKAO_ADMIN_SEARCH_SIZE_DEFAULT));
        String cacheKey = SEARCH_CACHE_PREFIX + normalizedKeyword.toLowerCase();

        List<KaKaoPlaceDocumentResult> cached = getCached(searchCache, cacheKey);
        if (cached != null) {
            return cached;
        }

        CompletableFuture<List<KaKaoPlaceDocumentResult>> inFlight = searchInFlight.computeIfAbsent(cacheKey, key -> {
            try {
                return CompletableFuture.completedFuture(
                        executeKakaoApiWithRetry(
                                "searchPlaces",
                                () -> kakaoPlaceClient().searchPlaces(normalizedKeyword, null, limit)
                        )
                );
            } catch (RuntimeException e) {
                CompletableFuture<List<KaKaoPlaceDocumentResult>> failedFuture = new CompletableFuture<>();
                failedFuture.completeExceptionally(e);
                return failedFuture;
            }
        });

        try {
            List<KaKaoPlaceDocumentResult> result = unwrap(inFlight);
            searchCache.put(cacheKey, new CachedValue<>(result, System.currentTimeMillis() + kakaoAdminCacheTtlMs));
            return result;
        } finally {
            searchInFlight.remove(cacheKey, inFlight);
        }
    }

    public KakaoPlaceDetailFetchResult fetchPlaceDetail(String externalId) {
        String normalizedExternalId = externalId == null ? "" : externalId.strip();
        if (normalizedExternalId.isBlank()) {
            return KakaoPlaceDetailFetchResult.unavailable();
        }

        String cacheKey = DETAIL_CACHE_PREFIX + normalizedExternalId;
        KakaoPlaceDetailFetchResult cached = getCached(detailCache, cacheKey);
        if (cached != null) {
            return cached;
        }

        CompletableFuture<KakaoPlaceDetailFetchResult> inFlight = detailInFlight.computeIfAbsent(cacheKey, key -> {
            try {
                return CompletableFuture.completedFuture(
                        executeKakaoApiWithRetry(
                                "fetchPlaceDetail",
                                () -> kakaoPlaceDetailClient().fetchPlaceDetailResult(normalizedExternalId)
                        )
                );
            } catch (RuntimeException e) {
                CompletableFuture<KakaoPlaceDetailFetchResult> failedFuture = new CompletableFuture<>();
                failedFuture.completeExceptionally(e);
                return failedFuture;
            }
        });

        try {
            KakaoPlaceDetailFetchResult result = unwrap(inFlight);
            if (result.status() == KakaoPlaceDetailFetchStatus.SUCCESS) {
                detailCache.put(
                        cacheKey,
                        new CachedValue<>(result, System.currentTimeMillis() + kakaoAdminCacheTtlMs)
                );
            }
            return result;
        } finally {
            detailInFlight.remove(cacheKey, inFlight);
        }
    }

    private <T> T executeKakaoApiWithRetry(
            String operationName,
            java.util.function.Supplier<T> supplier
    ) {
        int maxAttempts = Math.max(1, kakaoAdminMaxRetryAttempts);
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return executeWithSemaphore(() -> executeWithTimeout(supplier, operationName), operationName);
            } catch (RuntimeException e) {
                lastError = e;
                if (attempt >= maxAttempts) {
                    throw e;
                }

                long baseDelayMs = kakaoAdminRetryBaseDelayMs;
                long exponentialDelay = baseDelayMs * (1L << (attempt - 1));
                double jitterRatio = Math.max(0.0d, kakaoAdminRetryJitterRate);
                double jitterDelta = 1.0d + (ThreadLocalRandom.current().nextDouble(-jitterRatio, jitterRatio));
                long delayMs = (long) Math.max(0, Math.round(exponentialDelay * jitterDelta));

                log.warn(
                        "[retry:{}] attempt={}/{} failed: {}",
                        operationName,
                        attempt,
                        maxAttempts,
                        e.getMessage()
                );
                sleep(delayMs);
            }
        }

        throw lastError;
    }

    private <T> T executeWithSemaphore(java.util.function.Supplier<T> supplier, String operationName) {
        try {
            kakaoApiSemaphore.acquire();
            return supplier.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("kakao api semaphore interrupted: " + operationName, e);
        } catch (RuntimeException e) {
            throw e;
        } finally {
            kakaoApiSemaphore.release();
        }
    }

    private <T> T executeWithTimeout(java.util.function.Supplier<T> supplier, String operationName) {
        try {
            return java.util.concurrent.CompletableFuture.supplyAsync(supplier)
                    .orTimeout(KAKAO_ADMIN_API_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();

            if (cause instanceof java.util.concurrent.TimeoutException) {
                throw new CustomException(ErrorCode.KAKAO_API_ERROR);
            }

            if (cause instanceof RestClientResponseException restClientResponseException) {
                if (HttpStatus.TOO_MANY_REQUESTS.equals(restClientResponseException.getStatusCode())) {
                    throw new CustomException(ErrorCode.KAKAO_RATE_LIMIT_EXCEEDED);
                }
                throw new CustomException(ErrorCode.KAKAO_API_ERROR);
            }

            if (cause instanceof CustomException customException) {
                throw customException;
            }

            if (cause instanceof RuntimeException runtimeException) {
                throw new CustomException(ErrorCode.KAKAO_API_ERROR, runtimeException.getMessage());
            }

            throw new CustomException(ErrorCode.KAKAO_API_ERROR);
        }
    }

    private static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during kakao api retry delay", e);
        }
    }

    private KakaoPlaceClient kakaoPlaceClient() {
        KakaoPlaceClient client = kakaoPlaceClientProvider.getIfAvailable();
        if (client == null) {
            log.error(
                    "KakaoPlaceClient bean is not available. Check kakao.api.client.enabled and environment variable KAKAO_CLIENT_ENABLED"
            );
            throw new CustomException(ErrorCode.KAKAO_API_ERROR);
        }
        return client;
    }

    private KakaoPlaceDetailClient kakaoPlaceDetailClient() {
        KakaoPlaceDetailClient client = kakaoPlaceDetailClientProvider.getIfAvailable();
        if (client == null) {
            log.error(
                    "KakaoPlaceDetailClient bean is not available. Check kakao.api.client.enabled and environment variable KAKAO_CLIENT_ENABLED"
            );
            throw new CustomException(ErrorCode.KAKAO_API_ERROR);
        }
        return client;
    }

    private <T> T getCached(ConcurrentHashMap<String, CachedValue<T>> cache, String key) {
        CachedValue<T> cached = cache.get(key);
        if (cached == null) {
            return null;
        }
        if (cached.isExpired()) {
            cache.remove(key, cached);
            return null;
        }
        return cached.value();
    }

    private <T> T unwrap(CompletableFuture<T> futureResult) {
        try {
            return futureResult.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Unexpected async error", e);
        }
    }

    private record CachedValue<T>(T value, long expireAt) {
        boolean isExpired() {
            return System.currentTimeMillis() > expireAt;
        }
    }
}
