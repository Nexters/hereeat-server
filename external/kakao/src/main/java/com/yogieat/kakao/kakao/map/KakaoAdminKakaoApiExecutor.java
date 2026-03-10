package com.yogieat.kakao.kakao.map;

import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import com.yogieat.external.kakao.result.KaKaoPlaceDocumentResult;
import com.yogieat.external.kakao.result.KakaoPlaceDetailFetchResult;
import com.yogieat.external.kakao.result.KakaoPlaceDetailFetchStatus;
import com.yogieat.kakao.kakao.config.KakaoAdminClientProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

@Component
public class KakaoAdminKakaoApiExecutor {

    private static final Logger log = LoggerFactory.getLogger(KakaoAdminKakaoApiExecutor.class);
    private static final String SEARCH_CACHE_PREFIX = "admin-search:";
    private static final String DETAIL_CACHE_PREFIX = "admin-detail:";

    private final Semaphore kakaoApiSemaphore;
    private final int kakaoAdminSearchSize;
    private final int kakaoAdminMaxRetryAttempts;
    private final long kakaoAdminRetryBaseDelayMs;
    private final double kakaoAdminRetryJitterRate;
    private final long kakaoAdminApiTimeoutMs;
    private final long kakaoAdminCacheTtlMs;

    private final ConcurrentHashMap<String, CompletableFuture<List<KaKaoPlaceDocumentResult>>> searchInFlight =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<KakaoPlaceDetailFetchResult>> detailInFlight =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CachedValue<List<KaKaoPlaceDocumentResult>>> searchCache =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CachedValue<KakaoPlaceDetailFetchResult>> detailCache =
            new ConcurrentHashMap<>();

    public KakaoAdminKakaoApiExecutor(KakaoAdminClientProperties kakaoAdminClientProperties) {
        this.kakaoApiSemaphore = new Semaphore(Math.max(1, kakaoAdminClientProperties.concurrentPermits()));
        this.kakaoAdminSearchSize = kakaoAdminClientProperties.searchSize();
        this.kakaoAdminMaxRetryAttempts = kakaoAdminClientProperties.maxRetryAttempts();
        this.kakaoAdminRetryBaseDelayMs = kakaoAdminClientProperties.retryBaseDelayMs();
        this.kakaoAdminRetryJitterRate = kakaoAdminClientProperties.retryJitterRate();
        this.kakaoAdminApiTimeoutMs = kakaoAdminClientProperties.apiTimeoutMs();
        this.kakaoAdminCacheTtlMs = kakaoAdminClientProperties.cacheTtlMs();
    }

    public List<KaKaoPlaceDocumentResult> executeSearchPlaces(
            String placeName,
            String region,
            int requestedSize,
            Supplier<List<KaKaoPlaceDocumentResult>> supplier
    ) {
        String normalizedName = placeName == null ? "" : placeName.strip();
        if (normalizedName.isBlank()) {
            return List.of();
        }
        String normalizedRegion = region == null ? "" : region.strip();

        int limit = Math.max(1, Math.min(requestedSize, kakaoAdminSearchSize));
        String cacheKey = SEARCH_CACHE_PREFIX + normalizedName.toLowerCase()
                + "|" + normalizedRegion.toLowerCase() + "|" + limit;

        List<KaKaoPlaceDocumentResult> cached = getCached(searchCache, cacheKey);
        if (cached != null) {
            return cached;
        }

        CompletableFuture<List<KaKaoPlaceDocumentResult>> inFlight = searchInFlight.computeIfAbsent(cacheKey, key -> {
            try {
                return CompletableFuture.completedFuture(
                        executeKakaoApiWithRetry(
                                "searchPlaces",
                                () -> supplier.get()
                        )
                );
            } catch (RuntimeException e) {
                CompletableFuture<List<KaKaoPlaceDocumentResult>> failed = new CompletableFuture<>();
                failed.completeExceptionally(e);
                return failed;
            }
        });

        try {
            List<KaKaoPlaceDocumentResult> result = unwrap(inFlight);
            if (result.size() > limit) {
                result = new ArrayList<>(result.subList(0, limit));
            }
            searchCache.put(cacheKey, new CachedValue<>(result, System.currentTimeMillis() + kakaoAdminCacheTtlMs));
            return result;
        } finally {
            searchInFlight.remove(cacheKey, inFlight);
        }
    }

    public KakaoPlaceDetailFetchResult executePlaceDetail(
            String placeId,
            Supplier<KakaoPlaceDetailFetchResult> supplier
    ) {
        String normalizedId = placeId == null ? "" : placeId.strip();
        if (normalizedId.isBlank()) {
            return KakaoPlaceDetailFetchResult.unavailable();
        }

        String cacheKey = DETAIL_CACHE_PREFIX + normalizedId;
        KakaoPlaceDetailFetchResult cached = getCached(detailCache, cacheKey);
        if (cached != null) {
            return cached;
        }

        CompletableFuture<KakaoPlaceDetailFetchResult> inFlight = detailInFlight.computeIfAbsent(cacheKey, key -> {
            try {
                return CompletableFuture.completedFuture(
                        executeKakaoApiWithRetry(
                                "fetchPlaceDetailResult",
                                () -> supplier.get()
                        )
                );
            } catch (RuntimeException e) {
                CompletableFuture<KakaoPlaceDetailFetchResult> failed = new CompletableFuture<>();
                failed.completeExceptionally(e);
                return failed;
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

    private <T> T executeKakaoApiWithRetry(String operationName, Supplier<T> supplier) {
        int maxAttempts = Math.max(1, kakaoAdminMaxRetryAttempts);
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return executeWithSemaphore(() -> executeWithTimeout(supplier), operationName);
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

                log.warn("[retry:{}] attempt={}/{} failed: {}", operationName, attempt, maxAttempts, e.getMessage());
                sleep(delayMs);
            }
        }

        throw lastError;
    }

    private <T> T executeWithSemaphore(Supplier<T> supplier, String operationName) {
        boolean acquired = false;
        try {
            kakaoApiSemaphore.acquire();
            acquired = true;
            return supplier.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("kakao api semaphore interrupted: " + operationName, e);
        } finally {
            if (acquired) {
                kakaoApiSemaphore.release();
            }
        }
    }

    private <T> T executeWithTimeout(Supplier<T> supplier) {
        try {
            return CompletableFuture.supplyAsync(supplier)
                    .orTimeout(kakaoAdminApiTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
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
