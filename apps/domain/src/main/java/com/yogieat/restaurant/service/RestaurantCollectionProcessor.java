package com.yogieat.restaurant.service;

import com.yogieat.category.domain.value.LargeCategory;
import com.yogieat.common.Region;
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
import com.yogieat.restaurant.domain.Restaurant;
import com.yogieat.restaurant.domain.SuggestionRestaurant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final RestaurantCollectionWriteService restaurantCollectionWriteService;

    /**
     * Place enum에서 지원하는 지역 목록
     * Place enum을 지역 정보의 단일 진실 공급원(SSOT)으로 사용
     */
    private static final List<String> LOCATIONS = Arrays.stream(Region.values())
        .map(Region::getName)
        .toList();

    /**
     * LargeCategory enum에서 ANY를 제외한 음식 카테고리 목록
     * displayName을 사용하여 Gemini 프롬프트에 활용
     */
    private static final List<String> FOOD_CATEGORIES = Arrays.stream(LargeCategory.values())
        .filter(category -> category != LargeCategory.ANY)
        .map(LargeCategory::getDisplayName)
        .toList();

    /**
     * Enum 변환 캐시: Place name → Place enum
     * 매번 stream().filter()를 사용하지 않고 O(1) 조회
     */
    private static final Map<String, Region> PLACE_CACHE = Arrays.stream(Region.values())
        .collect(Collectors.toMap(Region::getName, Function.identity()));

    private static final int RESTAURANTS_PER_REQUEST = 10;
    private static final long RATE_LIMIT_DELAY_MS = 5000; // Gemini API rate limit을 위한 지연 시간 (5초)
    private static final int LOCATION_CATEGORY_BATCH_SIZE = 5; // 한 번에 처리할 location-category 조합 개수
    private static final long BATCH_DELAY_MS = 15000; // 배치 간 휴식 시간 (15초)
    private static final int MIN_REVIEW_COUNT = 30;

    /**
     * 지역별 맛집 수집 제한
     * GANGNAM, HONGDAE: 100개
     * 나머지 지역: 50개
     */
    private static final Map<Region, Integer> REGION_LIMITS = Map.of(
        Region.GANGNAM, 100,
        Region.HONGDAE, 100,
        Region.GONGDEOK, 50,
        Region.EULJIRO3GA, 50,
        Region.SADANG, 50,
        Region.JONGNO3GA, 50,
        Region.JAMSIL, 50,
        Region.SAMGAKJI, 50
    );
    private static final int DEFAULT_REGION_LIMIT = 50;  // 기본 제한

    public void collectAllRegions() {
        try {
            Map<Region, Long> regionCounts = loadRegionCounts();
            List<String> locationsToCollect = filterLocationsNeedingCollection(regionCounts);

            if (locationsToCollect.isEmpty()) {
                return;
            }

            List<Restaurant> restaurants = restaurantRepository.findAll();
            String restaurantNames = restaurants.stream()
                .map(Restaurant::name)
                .collect(Collectors.joining(", "));

            Map<LocationCategoryKey, List<SuggestionRestaurant>> allSuggestions =
                geminiClient.generateRestaurantsBatch(locationsToCollect, FOOD_CATEGORIES, restaurantNames, RESTAURANTS_PER_REQUEST);

            int totalProcessed = 0;
            int totalSuccess = 0;
            int totalFailed = 0;

            List<Map.Entry<LocationCategoryKey, List<SuggestionRestaurant>>> entries =
                new ArrayList<>(allSuggestions.entrySet());

            for (int i = 0; i < entries.size(); i += LOCATION_CATEGORY_BATCH_SIZE) {
                int endIndex = Math.min(i + LOCATION_CATEGORY_BATCH_SIZE, entries.size());
                List<Map.Entry<LocationCategoryKey, List<SuggestionRestaurant>>> batch =
                    entries.subList(i, endIndex);

                log.info("Processing batch {}/{}: {} location-category combinations",
                    (i / LOCATION_CATEGORY_BATCH_SIZE) + 1,
                    (entries.size() + LOCATION_CATEGORY_BATCH_SIZE - 1) / LOCATION_CATEGORY_BATCH_SIZE,
                    batch.size());

                for (Map.Entry<LocationCategoryKey, List<SuggestionRestaurant>> entry : batch) {
                    LocationCategoryKey key = entry.getKey();
                    List<SuggestionRestaurant> suggestions = entry.getValue();

                    Region region = getRegionFromLocationName(key.location());
                    if (isRegionLimitReached(region, regionCounts)) {
                        continue;
                    }

                    try {
                        int processed = processRestaurantsForLocation(key.location(), key.category(), suggestions);
                        totalProcessed += processed;
                        if (processed > 0) {
                            regionCounts.compute(region, (r, count) -> count == null ? (long) processed : count + processed);
                        }
                        totalSuccess++;
                    } catch (Exception e) {
                        log.error("Failed: {} - {}", key.location(), key.category(), e);
                        totalFailed++;
                    }
                }

                if (endIndex < entries.size()) {
                    try {
                        Thread.sleep(BATCH_DELAY_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("Thread interrupted during batch delay", e);
                    }
                }
            }

            log.info("Batch completed: {} saved, {} success, {} failed",
                totalProcessed, totalSuccess, totalFailed);

        } catch (Exception e) {
            log.error("Batch collection failed", e);
            throw new CustomException(ErrorCode.RESTAURANT_COLLECTION_FAILED);
        }
    }

    private Map<Region, Long> loadRegionCounts() {
        return restaurantRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        Restaurant::region,
                        Collectors.counting()
                ));
    }

    private List<String> filterLocationsNeedingCollection(Map<Region, Long> regionCounts) {
        List<String> locationsToCollect = new ArrayList<>();

        for (Region region : Region.values()) {
            long currentCount = regionCounts.getOrDefault(region, 0L);
            int limit = REGION_LIMITS.getOrDefault(region, DEFAULT_REGION_LIMIT);

            if (currentCount < limit) {
                locationsToCollect.add(region.getName());
                log.info("Region {} needs collection: {}/{} restaurants",
                    region.getName(), currentCount, limit);
            } else {
                log.info("Region {} reached limit: {}/{} restaurants (skipping)",
                    region.getName(), currentCount, limit);
            }
        }

        return locationsToCollect;
    }

    private boolean isRegionLimitReached(Region region, Map<Region, Long> regionCounts) {
        long currentCount = regionCounts.getOrDefault(region, 0L);
        int limit = REGION_LIMITS.getOrDefault(region, DEFAULT_REGION_LIMIT);
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

        return processRestaurantsForLocation(location, category, suggestions);
    }

    public int processRestaurantsForLocation(
        String location,
        String category,
        List<SuggestionRestaurant> suggestions
    ) {
        Region region = getRegionFromLocationName(location);
        RestaurantValidator.ValidationContext validationContext =
                restaurantCollectionWriteService.prepareValidationContext(region);

        int savedCount = 0;
        for (SuggestionRestaurant suggestion : suggestions) {
            RestaurantEnrichedData enrichedData = enrichRestaurantData(suggestion, location);
            if (enrichedData.isSkipped()) {
                continue;
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
                continue;
            }

            LargeCategory largeCategory = categoryResolution.largeCategory();
            String mediumCategory = categoryResolution.mediumCategory();

            boolean saved = restaurantCollectionWriteService.persistRestaurant(
                    suggestion,
                    region,
                    largeCategory,
                    mediumCategory,
                    enrichedData,
                    validationContext
            );
            if (saved) {
                savedCount++;
            }

            try {
                Thread.sleep(RATE_LIMIT_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Thread interrupted during rate limit delay", e);
            }
        }

        log.info("Saved {}/{} for {} - {}", savedCount, suggestions.size(), location, category);

        return savedCount;
    }

    private Region getRegionFromLocationName(String locationName) {
        Region region = PLACE_CACHE.get(locationName);
        if (region == null) {
            log.error("Unknown location name: {}. Available: {}", locationName, LOCATIONS);
            throw new CustomException(ErrorCode.INVALID_LOCATION_NAME);
        }
        return region;
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
