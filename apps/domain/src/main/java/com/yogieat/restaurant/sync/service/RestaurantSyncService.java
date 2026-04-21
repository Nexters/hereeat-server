package com.yogieat.restaurant.sync.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yogieat.category.service.CategoryService;
import com.yogieat.common.GeoJson;
import com.yogieat.common.GeoUtils;
import com.yogieat.external.kakao.KakaoPlaceClient;
import com.yogieat.external.kakao.KakaoPlaceDetailClient;
import com.yogieat.external.kakao.KakaoPlaceMapper;
import com.yogieat.external.kakao.result.KaKaoPlaceDocumentResult;
import com.yogieat.external.kakao.result.KakaoPlaceDetailData;
import com.yogieat.external.kakao.result.KakaoPlaceDetailFetchResult;
import com.yogieat.external.kakao.result.KakaoPlaceDetailFetchStatus;
import com.yogieat.external.kakao.result.KakaoRestaurantData;
import com.yogieat.restaurant.service.RestaurantCategoryResolver;
import com.yogieat.restaurant.service.RestaurantRepository;
import com.yogieat.restaurant.sync.domain.RestaurantSyncChunkResult;
import com.yogieat.restaurant.sync.domain.RestaurantSyncPatch;
import com.yogieat.restaurant.sync.domain.RestaurantSyncPatchCommand;
import com.yogieat.restaurant.sync.domain.RestaurantSyncResult;
import com.yogieat.restaurant.sync.domain.RestaurantSyncTarget;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

@Service
@Conditional(RestaurantSyncServiceCondition.class)
public class RestaurantSyncService {

    private static final Logger log = LoggerFactory.getLogger(RestaurantSyncService.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final double SYNC_REGION_RADIUS_KM = 1.0;
    private static final int DB_BATCH_SIZE = 300;
    private static final String SEARCH_CACHE_PREFIX = "search:";
    private static final String DETAIL_CACHE_PREFIX = "detail:";
    private static final int KAKAO_SYNC_CONCURRENT_PERMITS_DEFAULT = 8;
    private static final int KAKAO_SYNC_MAX_RETRY_ATTEMPTS_DEFAULT = 3;
    private static final long KAKAO_SYNC_RETRY_BASE_DELAY_MS_DEFAULT = 200L;
    private static final double KAKAO_SYNC_RETRY_JITTER_RATE_DEFAULT = 0.25d;
    private static final long KAKAO_SYNC_CACHE_TTL_MS_DEFAULT = 30_000L;

    private final RestaurantSyncChunkPersistenceService chunkPersistenceService;
    private final RestaurantRepository restaurantRepository;
    private final CategoryService categoryService;
    private final KakaoPlaceClient kakaoPlaceClient;
    private final KakaoPlaceDetailClient kakaoPlaceDetailClient;
    private final KakaoPlaceMapper kakaoPlaceMapper;
    private final Semaphore kakaoApiSemaphore;
    private final int kakaoSyncMaxRetryAttempts;
    private final long kakaoSyncRetryBaseDelayMs;
    private final double kakaoSyncRetryJitterRate;
    private final long kakaoSyncCacheTtlMs;
    private final ConcurrentHashMap<String, CompletableFuture<Optional<KaKaoPlaceDocumentResult>>> searchInFlight = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<KakaoPlaceDetailFetchResult>> detailInFlight = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CachedValue<Optional<KaKaoPlaceDocumentResult>>> searchCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CachedValue<KakaoPlaceDetailFetchResult>> detailCache = new ConcurrentHashMap<>();
    private final LongAdder kakaoSearchCallCount = new LongAdder();
    private final LongAdder kakaoDetailCallCount = new LongAdder();
    private final LongAdder kakaoSearchCallDurationMs = new LongAdder();
    private final LongAdder kakaoDetailCallDurationMs = new LongAdder();
    private final LongAdder kakaoRetryCount = new LongAdder();

    public RestaurantSyncService(
            RestaurantSyncChunkPersistenceService chunkPersistenceService,
            RestaurantRepository restaurantRepository,
            CategoryService categoryService,
            KakaoPlaceClient kakaoPlaceClient,
            KakaoPlaceDetailClient kakaoPlaceDetailClient,
            KakaoPlaceMapper kakaoPlaceMapper
    ) {
        this.chunkPersistenceService = chunkPersistenceService;
        this.restaurantRepository = restaurantRepository;
        this.categoryService = categoryService;
        this.kakaoPlaceClient = kakaoPlaceClient;
        this.kakaoPlaceDetailClient = kakaoPlaceDetailClient;
        this.kakaoPlaceMapper = kakaoPlaceMapper;
        this.kakaoApiSemaphore = new Semaphore(Math.max(1, KAKAO_SYNC_CONCURRENT_PERMITS_DEFAULT));
        this.kakaoSyncMaxRetryAttempts = KAKAO_SYNC_MAX_RETRY_ATTEMPTS_DEFAULT;
        this.kakaoSyncRetryBaseDelayMs = KAKAO_SYNC_RETRY_BASE_DELAY_MS_DEFAULT;
        this.kakaoSyncRetryJitterRate = KAKAO_SYNC_RETRY_JITTER_RATE_DEFAULT;
        this.kakaoSyncCacheTtlMs = KAKAO_SYNC_CACHE_TTL_MS_DEFAULT;
    }

    public RestaurantSyncResult syncOne(Long restaurantId) {
        RestaurantSyncChunkResult chunkResult = syncChunk(List.of(restaurantId), Runnable::run, 1);

        if (chunkResult.successCount() == 1) {
            return RestaurantSyncResult.success(restaurantId);
        }

        String message = chunkResult.errorMessages().isEmpty()
                ? "sync failed"
                : chunkResult.errorMessages().getFirst();
        return RestaurantSyncResult.failed(restaurantId, message);
    }

    public RestaurantSyncChunkResult syncChunk(List<Long> ids, Executor executor) {
        return syncChunk(ids, executor, ids.size());
    }

    public RestaurantSyncChunkResult syncChunk(List<Long> ids, Executor executor, int maxParallelism) {
        if (ids.isEmpty()) {
            return RestaurantSyncChunkResult.of(0, 0, 0, List.of());
        }
        int effectiveParallelism = Math.max(1, maxParallelism);
        long searchCallCountStart = kakaoSearchCallCount.sum();
        long detailCallCountStart = kakaoDetailCallCount.sum();
        long retryCountStart = kakaoRetryCount.sum();
        long searchCallDurationMsStart = kakaoSearchCallDurationMs.sum();
        long detailCallDurationMsStart = kakaoDetailCallDurationMs.sum();
        long chunkStartAt = System.nanoTime();

        List<RestaurantSyncTarget> targets = restaurantRepository.findSyncTargetsByIds(ids);
        long chunkTargetsLookupMs = (System.nanoTime() - chunkStartAt) / 1_000_000L;
        Map<Long, RestaurantSyncTarget> targetMap = new HashMap<>();
        targets.forEach(target -> targetMap.put(target.id(), target));
        Semaphore syncTargetSemaphore = new Semaphore(effectiveParallelism);

        List<CompletableFuture<SyncExecution>> futures = ids.stream()
                .map(id -> CompletableFuture.supplyAsync(
                        () -> syncTargetWithParallelismLimit(syncTargetSemaphore, id, targetMap.get(id)),
                        executor
                ))
                .toList();

        List<SyncExecution> executions = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        List<RestaurantSyncPatchCommand> patchCommands = executions.stream()
                .filter(SyncExecution::isPatchSuccess)
                .map(SyncExecution::patchCommand)
                .toList();
        List<Long> deleteIds = executions.stream()
                .filter(SyncExecution::isDeleteSuccess)
                .map(SyncExecution::restaurantId)
                .toList();

        List<String> errorMessages = new ArrayList<>();
        for (SyncExecution execution : executions) {
            if (!execution.success()) {
                if (execution.message() != null && !execution.message().isBlank() && errorMessages.size() < 10) {
                    errorMessages.add(execution.message());
                }
            }
        }

        int persistedSuccessCount = chunkPersistenceService.persistChunkChanges(
                patchCommands,
                deleteIds,
                errorMessages
        );
        int failedCount = ids.size() - persistedSuccessCount;

        long chunkDurationMs = (System.nanoTime() - chunkStartAt) / 1_000_000L;
        long chunkSearchCalls = kakaoSearchCallCount.sum() - searchCallCountStart;
        long chunkDetailCalls = kakaoDetailCallCount.sum() - detailCallCountStart;
        long chunkRetryCount = kakaoRetryCount.sum() - retryCountStart;
        long chunkSearchCallDurationMs = kakaoSearchCallDurationMs.sum() - searchCallDurationMsStart;
        long chunkDetailCallDurationMs = kakaoDetailCallDurationMs.sum() - detailCallDurationMsStart;

        log.debug(
                "syncChunk completed. requested={} success={} failed={} patchCount={} deleteCount={} latencyMs={} "
                        + "targetLookupMs={} searchCalls={} searchCallMs={} detailCalls={} detailCallMs={} retryCount={}",
                ids.size(),
                persistedSuccessCount,
                failedCount,
                patchCommands.size(),
                deleteIds.size(),
                chunkDurationMs,
                chunkTargetsLookupMs,
                chunkSearchCalls,
                chunkSearchCallDurationMs,
                chunkDetailCalls,
                chunkDetailCallDurationMs,
                chunkRetryCount
        );

        return RestaurantSyncChunkResult.of(
                ids.size(),
                persistedSuccessCount,
                failedCount,
                errorMessages
        );
    }

    private SyncExecution syncTargetWithParallelismLimit(
            Semaphore syncTargetSemaphore,
            Long requestedId,
            RestaurantSyncTarget target
    ) {
        boolean acquired = false;
        try {
            syncTargetSemaphore.acquire();
            acquired = true;
            return syncTarget(requestedId, target);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SyncExecution.failed(requestedId, "sync target semaphore interrupted");
        } finally {
            if (acquired) {
                syncTargetSemaphore.release();
            }
        }
    }

    private SyncExecution syncTarget(Long requestedId, RestaurantSyncTarget target) {
        if (target == null) {
            return SyncExecution.failed(requestedId, "restaurant not found");
        }
        if (!isWithinRegionRadius(target, target.location())) {
            return SyncExecution.delete(target.id());
        }

        try {
            SyncSource source = resolveSource(target);
            if (source == SyncSource.DELETE_TARGET) {
                return SyncExecution.delete(target.id());
            }
            if (source == null) {
                return SyncExecution.failed(
                        target.id(),
                        "kakao source unavailable"
                                + " (restaurantId=" + target.id()
                                + ", name=" + target.name()
                                + ", region=" + (target.region() == null ? "null" : target.region().name())
                                + ", externalId=" + target.externalId()
                                + ")"
                );
            }

            RestaurantSyncPatch patch = buildPatch(source, target.externalId());
            if (!isWithinRegionRadius(target, patch.location())) {
                return SyncExecution.delete(target.id());
            }
            RestaurantSyncPatchCommand command = RestaurantSyncPatchCommand.of(target.id(), patch);
            return SyncExecution.success(target.id(), command);
        } catch (Exception e) {
            log.error("Restaurant sync failed. restaurantId={}", target.id(), e);
            return SyncExecution.failed(target.id(), e.getMessage());
        }
    }

    private SyncSource resolveSource(RestaurantSyncTarget target) {
        if (target.externalId() != null && !target.externalId().isBlank()) {
            KakaoPlaceDetailFetchResult detailResult = fetchPlaceDetail(target.externalId());
            if (detailResult.status() == KakaoPlaceDetailFetchStatus.NOT_FOUND) {
                return SyncSource.DELETE_TARGET;
            }
            if (detailResult.status() == KakaoPlaceDetailFetchStatus.SUCCESS && detailResult.detail() != null) {
                return new SyncSource(target.externalId(), null, detailResult.detail());
            }
        }

        Optional<KaKaoPlaceDocumentResult> placeOpt = searchPlace(target.name(), target.region() == null ? null : target.region().getName());

        if (placeOpt.isEmpty()) {
            return null;
        }

        KaKaoPlaceDocumentResult place = placeOpt.get();
        KakaoPlaceDetailFetchResult detailResult = fetchPlaceDetail(place.id());
        KakaoPlaceDetailData detail = detailResult.status() == KakaoPlaceDetailFetchStatus.SUCCESS
                ? detailResult.detail()
                : null;

        return new SyncSource(place.id(), place, detail);
    }

    private KakaoPlaceDetailFetchResult fetchPlaceDetail(String placeId) {
        if (placeId == null || placeId.isBlank()) {
            return KakaoPlaceDetailFetchResult.unavailable();
        }

        String cacheKey = DETAIL_CACHE_PREFIX + placeId;
        KakaoPlaceDetailFetchResult cached = getCached(detailCache, cacheKey);
        if (cached != null) {
            return cached;
        }

        CompletableFuture<KakaoPlaceDetailFetchResult> inFlight = detailInFlight.computeIfAbsent(cacheKey, key -> {
            try {
                return CompletableFuture.completedFuture(
                        executeKakaoApiWithRetry(
                                "fetchPlaceDetailResult",
                                () -> {
                                    kakaoDetailCallCount.increment();
                                    return kakaoPlaceDetailClient.fetchPlaceDetailResult(placeId);
                                },
                                kakaoDetailCallDurationMs
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
            detailCache.put(cacheKey, new CachedValue<>(result, System.currentTimeMillis()
                    + kakaoSyncCacheTtlMs));
            return result;
        } finally {
            detailInFlight.remove(cacheKey, inFlight);
        }
    }

    private Optional<KaKaoPlaceDocumentResult> searchPlace(String placeName, String regionName) {
        String cacheKey = SEARCH_CACHE_PREFIX + normalizedKey(placeName, regionName);
        Optional<KaKaoPlaceDocumentResult> cached = getCached(searchCache, cacheKey);
        if (cached != null) {
            return cached;
        }

        CompletableFuture<Optional<KaKaoPlaceDocumentResult>> inFlight = searchInFlight.computeIfAbsent(cacheKey, key -> {
            try {
                return CompletableFuture.completedFuture(
                        executeKakaoApiWithRetry(
                                "searchPlace",
                                () -> {
                                    kakaoSearchCallCount.increment();
                                    return kakaoPlaceClient.searchPlace(placeName, regionName);
                                },
                                kakaoSearchCallDurationMs
                        )
                );
            } catch (RuntimeException e) {
                CompletableFuture<Optional<KaKaoPlaceDocumentResult>> failedFuture = new CompletableFuture<>();
                failedFuture.completeExceptionally(e);
                return failedFuture;
            }
        });

        try {
            Optional<KaKaoPlaceDocumentResult> result = unwrap(inFlight);
            searchCache.put(cacheKey, new CachedValue<>(result, System.currentTimeMillis()
                    + kakaoSyncCacheTtlMs));
            return result;
        } finally {
            searchInFlight.remove(cacheKey, inFlight);
        }
    }

    private static String normalizedKey(String placeName, String regionName) {
        String safeName = placeName == null ? "" : placeName.trim();
        String safeRegion = regionName == null ? "" : regionName.trim();
        return safeName + "|" + safeRegion;
    }

    private <T> T executeKakaoApiWithRetry(
            String operationName,
            Supplier<T> supplier,
            LongAdder durationMetric
    ) {
        long startedAt = System.nanoTime();
        try {
            return executeWithRetry(operationName, supplier);
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
            durationMetric.add(durationMs);
            log.debug("kakao api call completed. operation={} durationMs={}", operationName, durationMs);
        }
    }

    private <T> T executeWithRetry(String operationName, Supplier<T> supplier) {
        RuntimeException lastError = null;
        int maxAttempts = Math.max(1, kakaoSyncMaxRetryAttempts);
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return executeWithSemaphore(supplier, operationName);
            } catch (RuntimeException e) {
                lastError = e;
                if (attempt >= maxAttempts) {
                    throw e;
                }

                kakaoRetryCount.increment();
                long baseDelayMs = kakaoSyncRetryBaseDelayMs;
                long exponentialDelay = baseDelayMs * (1L << (attempt - 1));
                double jitterRatio = Math.max(0.0d, kakaoSyncRetryJitterRate);
                double jitterDelta = 1.0d;
                if (jitterRatio > 0.0d) {
                    jitterDelta += ThreadLocalRandom.current().nextDouble(-jitterRatio, jitterRatio);
                }
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

    private <T> T executeWithSemaphore(Supplier<T> supplier, String operationName) {
        boolean acquired = false;
        try {
            kakaoApiSemaphore.acquire();
            acquired = true;
            return supplier.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("kakao api semaphore interrupted: " + operationName, e);
        } catch (RuntimeException e) {
            throw e;
        } finally {
            if (acquired) {
                kakaoApiSemaphore.release();
            }
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

    private RestaurantSyncPatch buildPatch(SyncSource source, String currentExternalId) {
        KakaoRestaurantData searchData = null;
        if (source.place() != null) {
            searchData = kakaoPlaceMapper.toDomainData(source.place());
        }

        KakaoPlaceDetailData detail = source.detail();

        String externalId = source.externalId() != null ? source.externalId() : currentExternalId;
        String name = detail != null && detail.placeName() != null ? detail.placeName() : null;
        String mapUrl = resolveMapUrl(externalId, searchData);
        GeoJson.Point location = resolveLocation(searchData, detail);
        Double rating = detail != null ? detail.rating() : null;
        String imageUrl = detail != null ? detail.mainPhotoUrl() : null;
        String representativeReview = detail != null ? detail.representativeReview() : null;
        Integer reviewCount = detail != null ? detail.reviewCount() : null;
        Integer blogReviewCount = detail != null ? detail.blogReviewCount() : null;
        String representMenu = detail != null ? detail.representMenu() : null;
        Integer representMenuPrice = normalizeMenuPrice(detail != null ? detail.representMenuPrice() : null);
        String priceLevel = detail != null ? detail.priceLevel() : null;
        String aiMateSummaryTitle = detail != null ? detail.aiMateSummaryTitle() : null;
        String aiMateSummaryContents = detail != null ? toJson(detail.aiMateSummaryContents()) : null;
        Long categoryId = resolveCategoryId(detail);
        String offDays = detail != null ? offDaysToJson(detail.offDays()) : null;

        return new RestaurantSyncPatch(
                externalId,
                name,
                mapUrl,
                location,
                rating,
                imageUrl,
                representativeReview,
                reviewCount,
                blogReviewCount,
                representMenu,
                representMenuPrice,
                priceLevel,
                aiMateSummaryTitle,
                aiMateSummaryContents,
                detail != null ? detail.timeSlot() : null,
                categoryId,
                offDays
        );
    }

    private Long resolveCategoryId(KakaoPlaceDetailData detail) {
        if (detail == null) {
            return null;
        }

        RestaurantCategoryResolver.CategoryResolution categoryResolution =
                RestaurantCategoryResolver.resolveFromKakao(
                        detail.apiLargeCategory(),
                        detail.apiMediumCategory(),
                        detail.apiCategoryName2(),
                        detail.apiCategoryName3()
                );
        if (categoryResolution == null || categoryResolution.largeCategory() == null) {
            return null;
        }
        return categoryService.findOrCreateCategory(
                categoryResolution.largeCategory(),
                categoryResolution.mediumCategory()
        );
    }

    private String resolveMapUrl(String externalId, KakaoRestaurantData searchData) {
        if (searchData != null && searchData.mapUrl() != null && !searchData.mapUrl().isBlank()) {
            return normalizeMapUrl(searchData.mapUrl());
        }
        if (externalId == null || externalId.isBlank()) {
            return null;
        }
        return "https://place.map.kakao.com/" + externalId;
    }

    private GeoJson.Point resolveLocation(KakaoRestaurantData searchData, KakaoPlaceDetailData detail) {
        if (searchData != null && searchData.location() != null) {
            return searchData.location();
        }
        if (detail == null || detail.longitude() == null || detail.latitude() == null) {
            return null;
        }
        return new GeoJson.Point(List.of(detail.longitude(), detail.latitude()));
    }

    private Integer normalizeMenuPrice(Integer price) {
        if (price == null || price <= 0) {
            return null;
        }
        return price;
    }

    private String toJson(List<String> contents) {
        if (contents == null || contents.isEmpty()) {
            return null;
        }

        try {
            return OBJECT_MAPPER.writeValueAsString(contents);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String offDaysToJson(List<LocalDate> offDays) {
        if (offDays == null) {
            return null;
        }
        if (offDays.isEmpty()) {
            return "[]";
        }
        return toJson(offDays.stream().map(LocalDate::toString).toList());
    }

    private String normalizeMapUrl(String mapUrl) {
        if (mapUrl == null || mapUrl.isBlank()) {
            return null;
        }

        if (mapUrl.startsWith("https:")) {
            return mapUrl;
        }

        if (mapUrl.startsWith("http:")) {
            return "https:" + mapUrl.substring("http:".length());
        }

        if (mapUrl.startsWith("//")) {
            return "https:" + mapUrl;
        }

        return mapUrl;
    }

    private boolean isWithinRegionRadius(RestaurantSyncTarget target, GeoJson.Point resolvedPoint) {
        if (target == null || target.region() == null || target.region().getCoordinatesStandard() == null) {
            return false;
        }

        return isWithinDistance(target.region().getCoordinatesStandard(), resolvedPoint);
    }

    private boolean isWithinDistance(GeoJson.Point centerPoint, GeoJson.Point restaurantPoint) {
        if (!GeoUtils.isValidPoint(centerPoint) || !GeoUtils.isValidPoint(restaurantPoint)) {
            return false;
        }

        double distance = GeoUtils.calculateDistanceKm(centerPoint, restaurantPoint);
        return distance <= SYNC_REGION_RADIUS_KM;
    }

    private record CachedValue<T>(T value, long expireAt) {
        boolean isExpired() {
            return System.currentTimeMillis() > expireAt;
        }
    }

    private record SyncSource(String externalId, KaKaoPlaceDocumentResult place, KakaoPlaceDetailData detail) {
        private static final SyncSource DELETE_TARGET = new SyncSource(null, null, null);
    }

    private record SyncExecution(
            Long restaurantId,
            boolean success,
            boolean deleteTarget,
            RestaurantSyncPatchCommand patchCommand,
            String message
    ) {
        static SyncExecution success(Long restaurantId, RestaurantSyncPatchCommand command) {
            return new SyncExecution(restaurantId, true, false, command, null);
        }

        static SyncExecution delete(Long restaurantId) {
            return new SyncExecution(restaurantId, true, true, null, null);
        }

        static SyncExecution failed(Long restaurantId, String message) {
            return new SyncExecution(restaurantId, false, false, null, message);
        }

        boolean isPatchSuccess() {
            return success && !deleteTarget && patchCommand != null;
        }

        boolean isDeleteSuccess() {
            return success && deleteTarget;
        }
    }
}
