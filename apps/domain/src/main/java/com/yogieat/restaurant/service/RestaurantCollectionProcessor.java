package com.yogieat.restaurant.service;

import com.yogieat.category.domain.value.LargeCategory;
import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import com.yogieat.external.LocationCategoryKey;
import com.yogieat.external.ai.GeminiClient;
import com.yogieat.external.kakao.KakaoPlaceClient;
import com.yogieat.external.kakao.KakaoPlaceDetailClient;
import com.yogieat.external.kakao.KakaoPlaceMapper;
import com.yogieat.external.kakao.result.KaKaoPlaceDocumentResult;
import com.yogieat.external.kakao.result.KakaoPlaceDetailData;
import com.yogieat.external.kakao.result.KakaoRestaurantData;
import com.yogieat.region.domain.RegionMaster;
import com.yogieat.region.domain.RegionSummary;
import com.yogieat.region.service.RegionService;
import com.yogieat.restaurant.domain.SuggestionRestaurant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

@Service
@Conditional(RestaurantCollectionProcessorCondition.class)
@RequiredArgsConstructor
public class RestaurantCollectionProcessor {

    private static final Logger log = LoggerFactory.getLogger(RestaurantCollectionProcessor.class);

    private final GeminiClient geminiClient;
    private final KakaoPlaceClient kakaoPlaceClient;
    private final KakaoPlaceDetailClient kakaoPlaceDetailClient;
    private final KakaoPlaceMapper kakaoPlaceMapper;
    private final RestaurantRepository restaurantRepository;
    private final RegionService regionService;
    private final RestaurantCollectionWriteService restaurantCollectionWriteService;

    /**
     * LargeCategory enum에서 ANY를 제외한 음식 카테고리 목록
     * displayName을 사용하여 Gemini 프롬프트에 활용
     */
    private static final List<String> FOOD_CATEGORIES = Arrays.stream(LargeCategory.values())
        .filter(category -> category != LargeCategory.ANY)
        .map(LargeCategory::getDisplayName)
        .toList();

    private static final int RESTAURANTS_PER_REQUEST = 10;
    private static final int KAKAO_COLLECTION_CONCURRENT_PERMITS = 3;
    private static final int MIN_REVIEW_COUNT = 30;
    private static final List<String> HIGH_VOLUME_REGION_CODES = List.of("GANGNAM", "HONGDAE");
    private static final int DEFAULT_REGION_LIMIT = 50;

    private final Semaphore kakaoApiSemaphore = new Semaphore(KAKAO_COLLECTION_CONCURRENT_PERMITS);

    public void collectAllRegions() {
        try {
            List<RegionSummary> activeRegions = regionService.findActiveRegionSummaries();
            if (activeRegions.isEmpty()) {
                return;
            }

            ConcurrentHashMap<Long, Long> regionCounts = new ConcurrentHashMap<>(loadRegionCounts(activeRegions));
            List<RegionSummary> regionsToCollect = filterRegionsNeedingCollection(activeRegions, regionCounts);

            if (regionsToCollect.isEmpty()) {
                return;
            }

            List<String> locationsToCollect = regionsToCollect.stream()
                    .map(summary -> summary.region().displayName())
                    .toList();
            Map<String, RegionSummary> regionByLocation = regionsToCollect.stream()
                    .collect(Collectors.toMap(
                            summary -> summary.region().displayName(),
                            Function.identity(),
                            keepExistingRegion()
                    ));

            String restaurantNames = restaurantRepository.findAll().stream()
                .map(com.yogieat.restaurant.domain.Restaurant::name)
                .collect(Collectors.joining(", "));

            Map<LocationCategoryKey, List<SuggestionRestaurant>> allSuggestions =
                geminiClient.generateRestaurantsBatch(locationsToCollect, FOOD_CATEGORIES, restaurantNames, RESTAURANTS_PER_REQUEST);

            AtomicInteger totalProcessed = new AtomicInteger(0);
            AtomicInteger totalSuccess = new AtomicInteger(0);
            AtomicInteger totalFailed = new AtomicInteger(0);

            log.info("Processing {} location-category combinations with virtual threads",
                    allSuggestions.size());

            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                List<Future<Void>> futures = new ArrayList<>();

                for (Map.Entry<LocationCategoryKey, List<SuggestionRestaurant>> entry : allSuggestions.entrySet()) {
                    LocationCategoryKey key = entry.getKey();
                    List<SuggestionRestaurant> suggestions = entry.getValue();

                    RegionSummary regionSummary = regionByLocation.get(key.location());
                    if (regionSummary == null || isRegionLimitReached(regionSummary.region(), regionCounts)) {
                        continue;
                    }

                    futures.add(executor.submit(() -> {
                        try {
                            int processed = processRestaurantsForRegion(regionSummary.region(), key.category(), suggestions);
                            totalProcessed.addAndGet(processed);
                            if (processed > 0) {
                                regionCounts.compute(
                                        regionSummary.region().id(),
                                        (regionId, count) -> count == null ? (long) processed : count + processed
                                );
                            }
                            totalSuccess.incrementAndGet();
                        } catch (Exception e) {
                            log.error("Failed: {} - {}", key.location(), key.category(), e);
                            totalFailed.incrementAndGet();
                        }
                        return null;
                    }));
                }

                for (Future<Void> future : futures) {
                    try {
                        future.get();
                    } catch (ExecutionException e) {
                        log.error("Unexpected error in collection task", e.getCause());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("Collection interrupted");
                        break;
                    }
                }
            }

            log.info("Batch completed: {} saved, {} success, {} failed",
                totalProcessed.get(), totalSuccess.get(), totalFailed.get());

        } catch (Exception e) {
            log.error("Batch collection failed", e);
            throw new CustomException(ErrorCode.RESTAURANT_COLLECTION_FAILED);
        }
    }

    private Map<Long, Long> loadRegionCounts(List<RegionSummary> activeRegions) {
        return activeRegions.stream()
                .collect(Collectors.toMap(
                        summary -> summary.region().id(),
                        RegionSummary::restaurantCount
                ));
    }

    private List<RegionSummary> filterRegionsNeedingCollection(
            List<RegionSummary> activeRegions,
            Map<Long, Long> regionCounts
    ) {
        List<RegionSummary> regionsToCollect = new ArrayList<>();

        for (RegionSummary summary : activeRegions) {
            RegionMaster region = summary.region();
            long currentCount = regionCounts.getOrDefault(region.id(), 0L);
            int limit = getRegionLimit(region);

            if (currentCount < limit) {
                regionsToCollect.add(summary);
                log.info("Region {} needs collection: {}/{} restaurants",
                    region.displayName(), currentCount, limit);
            } else {
                log.info("Region {} reached limit: {}/{} restaurants (skipping)",
                    region.displayName(), currentCount, limit);
            }
        }

        return regionsToCollect;
    }

    private boolean isRegionLimitReached(RegionMaster region, Map<Long, Long> regionCounts) {
        long currentCount = regionCounts.getOrDefault(region.id(), 0L);
        int limit = getRegionLimit(region);
        return currentCount >= limit;
    }

    /**
     * 특정 지역과 카테고리에 대한 맛집 데이터 수집 (단일 API 호출용 - 레거시)
     *
     * @deprecated Use batch processing via collectAllRegions() for better performance
     */
    @Deprecated
    public int collectRestaurantsForLocation(String location, String category) {
        List<SuggestionRestaurant> suggestions = geminiClient.generateRestaurants(
            location, category, RESTAURANTS_PER_REQUEST
        );

        return processRestaurantsForRegion(regionService.getActiveRegionByDisplayName(location), category, suggestions);
    }

    /**
     * Virtual Thread + Semaphore 기반 병렬 맛집 수집.
     *
     * <p>각 suggestion을 별도 virtual thread에서 처리하되,
     * Kakao API 호출은 Semaphore({@value KAKAO_COLLECTION_CONCURRENT_PERMITS} permits)로 동시성을 제어합니다.</p>
     */
    public int processRestaurantsForRegion(
        RegionMaster region,
        String category,
        List<SuggestionRestaurant> suggestions
    ) {
        RestaurantValidator.ValidationContext validationContext =
                restaurantCollectionWriteService.prepareValidationContext(region);

        AtomicInteger savedCount = new AtomicInteger(0);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Void>> futures = new ArrayList<>();

            for (SuggestionRestaurant suggestion : suggestions) {
                futures.add(executor.submit(() -> {
                    processSingleSuggestion(
                            suggestion, region, category, validationContext, savedCount
                    );
                    return null;
                }));
            }

            for (Future<Void> future : futures) {
                try {
                    future.get();
                } catch (ExecutionException e) {
                    log.error("Failed to process suggestion", e.getCause());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        log.info("Saved {}/{} for {} - {}", savedCount.get(), suggestions.size(), region.displayName(), category);
        return savedCount.get();
    }

    private void processSingleSuggestion(
            SuggestionRestaurant suggestion,
            RegionMaster region,
            String category,
            RestaurantValidator.ValidationContext validationContext,
            AtomicInteger savedCount
    ) {
        RestaurantEnrichedData enrichedData;
        try {
            kakaoApiSemaphore.acquire();
            try {
                enrichedData = enrichRestaurantData(suggestion, region.displayName());
            } finally {
                kakaoApiSemaphore.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        if (enrichedData.isSkipped()) {
            return;
        }

        RestaurantCategoryResolver.CategoryResolution categoryResolution =
                RestaurantCategoryResolver.resolveForCollection(
                        category,
                        suggestion.largeCategory(),
                        suggestion.mediumCategory(),
                        enrichedData.apiLargeCategory(),
                        enrichedData.apiMediumCategory(),
                        enrichedData.apiCategoryName2(),
                        enrichedData.apiCategoryName3()
                );

        if (categoryResolution == null) {
            log.info("Skipping restaurant due to unresolved category: {} (requested={}, kakaoName2={}, kakaoName3={})",
                    suggestion.name(),
                    category,
                    enrichedData.apiCategoryName2(),
                    enrichedData.apiCategoryName3());
            return;
        }

        boolean saved = restaurantCollectionWriteService.persistRestaurant(
                suggestion,
                region,
                categoryResolution.largeCategory(),
                categoryResolution.mediumCategory(),
                enrichedData,
                validationContext
        );
        if (saved) {
            savedCount.incrementAndGet();
        }
    }

    private RestaurantEnrichedData enrichRestaurantData(SuggestionRestaurant suggestion, String locationName) {
        RestaurantEnrichedData enrichedData = RestaurantEnrichedData.fromSuggestion(suggestion);
        Optional<KaKaoPlaceDocumentResult> placeOpt = kakaoPlaceClient.searchPlace(suggestion.name(), locationName);

        if (placeOpt.isEmpty()) {
            log.warn("Kakao place not found for: {} in {}", suggestion.name(), locationName);
            return enrichedData;
        }

        KaKaoPlaceDocumentResult place = placeOpt.get();
        if (isCafeOrCoffee(place)) {
            log.info("Skipping cafe/coffee shop: {} (category: {})", suggestion.name(), place.categoryName());
            return RestaurantEnrichedData.skipped();
        }

        applySearchData(enrichedData, place);
        applyDetailData(enrichedData, suggestion, locationName, place.id());
        return enrichedData;
    }

    private boolean isCafeOrCoffee(KaKaoPlaceDocumentResult place) {
        String categoryName = place.categoryName();
        return categoryName != null && (categoryName.contains("카페") || categoryName.contains("커피"));
    }

    private void applySearchData(RestaurantEnrichedData enrichedData, KaKaoPlaceDocumentResult place) {
        KakaoRestaurantData data = kakaoPlaceMapper.toDomainData(place);
        enrichedData.externalId = data.externalId();
        enrichedData.mapUrl = normalizeMapUrl(data.mapUrl());
        enrichedData.geoJsonLocation = data.location();
    }

    private void applyDetailData(
            RestaurantEnrichedData enrichedData,
            SuggestionRestaurant suggestion,
            String locationName,
            String placeId
    ) {
        Optional<KakaoPlaceDetailData> detailOpt = kakaoPlaceDetailClient.fetchPlaceDetail(placeId);
        if (detailOpt.isEmpty()) {
            return;
        }

        KakaoPlaceDetailData detail = detailOpt.get();
        if (detail.rating() == null) {
            log.info("Skipping restaurant due to filtering (non-restaurant or invalid rating): {} in {}",
                    suggestion.name(), locationName);
            enrichedData.skip = true;
            return;
        }

        enrichedData.rating = detail.rating();
        enrichedData.placeName = detail.placeName();
        enrichedData.imageUrl = hasText(detail.mainPhotoUrl()) ? detail.mainPhotoUrl() : null;
        enrichedData.representativeReview = hasText(detail.representativeReview()) ? detail.representativeReview() : null;
        enrichedData.reviewCount = detail.reviewCount();
        enrichedData.blogReviewCount = detail.blogReviewCount();
        enrichedData.representMenu = detail.representMenu();
        enrichedData.representMenuPrice = normalizeMenuPrice(detail.representMenuPrice());
        enrichedData.priceLevel = detail.priceLevel();
        enrichedData.aiMateSummaryTitle = detail.aiMateSummaryTitle();
        enrichedData.aiMateSummaryContents = detail.aiMateSummaryContents();
        enrichedData.timeSlot = detail.timeSlot();
        enrichedData.apiCategoryName2 = detail.apiCategoryName2();
        enrichedData.apiCategoryName3 = detail.apiCategoryName3();
        enrichedData.apiLargeCategory = detail.apiLargeCategory();
        enrichedData.apiMediumCategory = detail.apiMediumCategory();

        if (!hasText(enrichedData.aiMateSummaryTitle)) {
            log.info("Skipping restaurant due to missing ai_mate data: {}", suggestion.name());
            enrichedData.skip = true;
            return;
        }

        if (isInsufficientReviewCount(enrichedData.reviewCount, enrichedData.blogReviewCount)) {
            log.info("Skipping restaurant due to insufficient reviews: {} (reviews: {}, blog: {})",
                    suggestion.name(), enrichedData.reviewCount, enrichedData.blogReviewCount);
            enrichedData.skip = true;
        }
    }

    private int getRegionLimit(RegionMaster region) {
        if (region != null && HIGH_VOLUME_REGION_CODES.contains(region.code())) {
            return 100;
        }
        return DEFAULT_REGION_LIMIT;
    }

    private BinaryOperator<RegionSummary> keepExistingRegion() {
        return (existing, ignored) -> existing;
    }

    private String normalizeMapUrl(String originalMapUrl) {
        if (originalMapUrl == null || originalMapUrl.isBlank()) {
            return originalMapUrl;
        }
        if (originalMapUrl.startsWith("https:")) {
            return originalMapUrl;
        }
        if (originalMapUrl.startsWith("http:")) {
            return "https:" + originalMapUrl.substring("http:".length());
        }
        if (originalMapUrl.startsWith("//")) {
            return "https:" + originalMapUrl;
        }
        return originalMapUrl;
    }

    private boolean isInsufficientReviewCount(Integer reviewCount, Integer blogReviewCount) {
        return reviewCount == null
                || reviewCount < MIN_REVIEW_COUNT
                || blogReviewCount == null
                || blogReviewCount < MIN_REVIEW_COUNT;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private Integer normalizeMenuPrice(Integer price) {
        if (price == null || price <= 0) {
            return null;
        }
        return price;
    }
}
