package com.yogieat.restaurant.service;

import com.yogieat.category.domain.value.LargeCategory;
import com.yogieat.category.service.CategoryService;
import com.yogieat.common.GeoJson;
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
import com.yogieat.gathering.domain.value.TimeSlot;
import com.yogieat.restaurant.domain.CreateRestaurant;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RestaurantCollectionProcessor {

    private final GeminiClient geminiClient;
    private final KakaoPlaceClient kakaoPlaceClient;
    private final KakaoPlaceDetailClient kakaoPlaceDetailClient;
    private final KakaoPlaceMapper kakaoPlaceMapper;
    private final RestaurantRepository restaurantRepository;
    private final CategoryService categoryService;
    private final RestaurantValidator restaurantValidator;

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

    /**
     * Enum 변환 캐시: LargeCategory displayName → LargeCategory enum
     * 매번 stream().filter()를 사용하지 않고 O(1) 조회
     */
    private static final Map<String, LargeCategory> LARGE_CATEGORY_CACHE = Arrays.stream(LargeCategory.values())
        .collect(Collectors.toMap(LargeCategory::getDisplayName, Function.identity()));

    private static final int RESTAURANTS_PER_REQUEST = 5;
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

    /**
     * Place enum에 정의된 모든 지역에 대해 맛집 데이터 수집
     * 배치 처리 최적화:
     * - Gemini API 호출 1회로 모든 location × category 조합 처리
     * - API 호출 10회 → 1회 (90% 감소)
     * - 처리 시간 대폭 단축 (rate limit 대기 제거)
     * - 지역별 수집 제한 적용 (GANGNAM, HONGDAE: 100개, 나머지: 50개)
     */
    @Transactional
    public void collectAllRegions() {
        try {
            // 1. 지역별 현재 맛집 수 조회 및 수집 필요 지역 필터링
            List<String> locationsToCollect = filterLocationsNeedingCollection();

            if (locationsToCollect.isEmpty()) {
                return;
            }

            List<Restaurant> restaurants = restaurantRepository.findAll();

            String restaurantNames = restaurants.stream()
                .map(Restaurant::name)
                .collect(Collectors.joining(", "));

            // 2. 배치 API 호출: 필터링된 location × category 조합만 요청
            Map<LocationCategoryKey, List<SuggestionRestaurant>> allSuggestions =
                geminiClient.generateRestaurantsBatch(locationsToCollect, FOOD_CATEGORIES, restaurantNames, RESTAURANTS_PER_REQUEST);

            // 3. 각 location-category 조합별로 데이터 처리 (페이징)
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

                    // 지역별 제한 체크 (실시간)
                    Region region = getRegionFromLocationName(key.location());
                    if (isRegionLimitReached(region)) {
                        continue;
                    }

                    try {
                        int processed = processRestaurantsForLocation(key.location(), key.category(), suggestions);
                        totalProcessed += processed;
                        totalSuccess++;

                    } catch (Exception e) {
                        log.error("Failed: {} - {}", key.location(), key.category(), e);
                        totalFailed++;
                    }
                }

                // 배치 간 휴식 (마지막 배치가 아닌 경우)
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


    /**
     * 수집이 필요한 지역 목록 필터링
     * 각 지역의 현재 맛집 수가 제한에 도달하지 않은 지역만 반환
     *
     * @return 수집이 필요한 지역명 목록
     */
    private List<String> filterLocationsNeedingCollection() {
        List<String> locationsToCollect = new ArrayList<>();

        for (Region region : Region.values()) {
            long currentCount = restaurantRepository.countByRegion(region);
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

    /**
     * 지역의 맛집 수집 제한에 도달했는지 확인
     *
     * @param region 확인할 지역
     * @return 제한 도달 여부
     */
    private boolean isRegionLimitReached(Region region) {
        long currentCount = restaurantRepository.countByRegion(region);
        int limit = REGION_LIMITS.getOrDefault(region, DEFAULT_REGION_LIMIT);
        return currentCount >= limit;
    }

    /**
     * 특정 지역과 카테고리에 대한 맛집 데이터 수집 (단일 API 호출용 - 레거시)
     *
     * @deprecated Use batch processing via collectAllRegions() for better performance
     */
    @Deprecated
    @Transactional
    public int collectRestaurantsForLocation(String location, String category) {
        Region region = getRegionFromLocationName(location);
        RestaurantValidator.ValidationContext validationContext =
                restaurantValidator.prepareForBatchValidation(region);

        List<SuggestionRestaurant> suggestions = geminiClient.generateRestaurants(
            location, category, RESTAURANTS_PER_REQUEST
        );

        int savedCount = 0;
        for (SuggestionRestaurant suggestion : suggestions) {
            boolean saved = processRestaurant(suggestion, region, location, validationContext);
            if (saved) {
                savedCount++;
            }
        }

        log.info("Saved {}/{} for {} - {}", savedCount, suggestions.size(), location, category);

        return savedCount;
    }

    /**
     * 배치에서 수집된 suggestions를 처리하여 저장
     *
     * 데이터 처리 전략:
     * 1. Place enum 변환 및 캐시 준비
     * 2. 카테고리 정보로 카테고리 조회 또는 생성 (대카테고리, 중카테고리)
     * 3. Kakao Place API로 데이터 보강 (위치, 지도 URL, 외부 ID)
     * 4. 모든 생성된 맛집 저장 (Kakao 보강 실패 시에도 저장)
     *
     * @param location 지역명 (Place enum에서 가져옴)
     * @param category 음식 카테고리
     * @param suggestions Gemini에서 생성된 레스토랑 목록
     * @return 성공적으로 저장된 맛집 개수
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int processRestaurantsForLocation(
        String location,
        String category,
        List<SuggestionRestaurant> suggestions
    ) {
        // 1. location을 Place enum으로 변환 (배치 검증용)
        Region region = getRegionFromLocationName(location);

        // 2. Validator 캐시 준비: 해당 Place의 기존 레스토랑 로드 (N번 → 1번 DB 쿼리)
        RestaurantValidator.ValidationContext validationContext =
                restaurantValidator.prepareForBatchValidation(region);

        // 3. 각 suggestion 처리 (카테고리 생성 + Gemini 데이터 + Kakao 데이터 보강)
        int savedCount = 0;
        for (SuggestionRestaurant suggestion : suggestions) {
            boolean saved = processRestaurant(suggestion, region, location, validationContext);
            if (saved) {
                savedCount++;
            }

            // API Rate Limit 방지를 위한 지연
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

    /**
     * location 이름을 Place enum으로 변환 (캐시 사용, O(1) 조회)
     * @param locationName Place enum의 location 이름 (예: "홍대입구역")
     * @return Place enum
     * @throws CustomException 알 수 없는 location 이름인 경우
     */
    private Region getRegionFromLocationName(String locationName) {
        Region region = PLACE_CACHE.get(locationName);
        if (region == null) {
            log.error("Unknown location name: {}. Available: {}", locationName, LOCATIONS);
            throw new CustomException(ErrorCode.INVALID_LOCATION_NAME);
        }
        return region;
    }

    /**
     * largeCategory displayName을 LargeCategory enum으로 변환 (캐시 사용, O(1) 조회)
     * @param displayName LargeCategory enum의 displayName (예: "한식")
     * @return LargeCategory enum
     * @throws CustomException 알 수 없는 카테고리 이름인 경우
     */
    private LargeCategory getLargeCategoryFromDisplayName(String displayName) {
        LargeCategory category = LARGE_CATEGORY_CACHE.get(displayName);
        if (category == null) {
            log.error("Unknown large category: {}. Available: {}", displayName, FOOD_CATEGORIES);
            throw new CustomException(ErrorCode.INVALID_CATEGORY_NAME);
        }
        return category;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected boolean processRestaurant(
            SuggestionRestaurant suggestion,
            Region restaurantRegion,
            String locationName,
            RestaurantValidator.ValidationContext validationContext
    ) {
        try {
            Long categoryId = resolveCategoryId(suggestion);
            EnrichedRestaurantData enrichedData = enrichRestaurantData(suggestion, locationName);

            if (enrichedData.isSkipped()) {
                return false;
            }

            if (enrichedData.externalId() == null || enrichedData.externalId().isBlank()) {
                log.warn("Skipping restaurant due to missing externalId: {} in {}",
                    suggestion.name(), locationName);
                return false;
            }

            RestaurantValidator.ValidationResult validationResult =
                    restaurantValidator.duplicateValidateWithCache(
                            validationContext,
                            suggestion,
                            enrichedData.externalId()
                    );

            if (!validationResult.isValid()) {
                return false;
            }

            CreateRestaurant createRestaurant = CreateRestaurant.of(
                    suggestion,
                    enrichedData.placeName(),
                    categoryId,
                    enrichedData.externalId(),
                    enrichedData.mapUrl(),
                    enrichedData.geoJsonLocation(),
                    enrichedData.rating(),
                    enrichedData.imageUrl(),
                    enrichedData.representativeReview(),
                    restaurantRegion,
                    enrichedData.reviewCount(),
                    enrichedData.blogReviewCount(),
                    enrichedData.representMenu(),
                    enrichedData.representMenuPrice(),
                    enrichedData.priceLevel(),
                    enrichedData.aiMateSummaryTitle(),
                    enrichedData.aiMateSummaryContents(),
                    enrichedData.timeSlot()
            );

            restaurantRepository.save(createRestaurant);

            restaurantValidator.addToCache(
                    validationContext,
                    enrichedData.externalId(),
                    suggestion.name(),
                    suggestion.address()
            );

            return true;
        } catch (Exception e) {
            log.error("Failed to process restaurant: {} in {}", suggestion.name(), locationName, e);
            // 예외 발생 시 이 트랜잭션만 롤백 (REQUIRES_NEW)
            return false;
        }
    }

    private Long resolveCategoryId(SuggestionRestaurant suggestion) {
        LargeCategory largeCategory = getLargeCategoryFromDisplayName(suggestion.largeCategory());
        return categoryService.findOrCreateCategory(largeCategory, suggestion.mediumCategory());
    }

    private EnrichedRestaurantData enrichRestaurantData(SuggestionRestaurant suggestion, String locationName) {
        EnrichedRestaurantData enrichedData = EnrichedRestaurantData.fromSuggestion(suggestion);
        Optional<KaKaoPlaceDocumentResult> placeOpt = kakaoPlaceClient.searchPlace(suggestion.name(), locationName);

        if (placeOpt.isEmpty()) {
            log.warn("Kakao place not found for: {} in {}", suggestion.name(), locationName);
            return enrichedData;
        }

        KaKaoPlaceDocumentResult place = placeOpt.get();
        if (isCafeOrCoffee(place)) {
            log.info("Skipping cafe/coffee shop: {} (category: {})", suggestion.name(), place.categoryName());
            return EnrichedRestaurantData.skipped();
        }

        applySearchData(enrichedData, place);
        applyDetailData(enrichedData, suggestion, locationName, place.id());
        return enrichedData;
    }

    private boolean isCafeOrCoffee(KaKaoPlaceDocumentResult place) {
        String categoryName = place.categoryName();
        return categoryName != null && (categoryName.contains("카페") || categoryName.contains("커피"));
    }

    private void applySearchData(EnrichedRestaurantData enrichedData, KaKaoPlaceDocumentResult place) {
        KakaoRestaurantData data = kakaoPlaceMapper.toDomainData(place);
        enrichedData.externalId = data.externalId();
        enrichedData.mapUrl = normalizeMapUrl(data.mapUrl());
        enrichedData.geoJsonLocation = data.location();
    }

    private void applyDetailData(
            EnrichedRestaurantData enrichedData,
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

    private static class EnrichedRestaurantData {
        private String externalId;
        private String mapUrl;
        private GeoJson.Point geoJsonLocation;
        private Double rating;
        private String imageUrl;
        private String placeName;
        private String representativeReview;
        private Integer reviewCount;
        private Integer blogReviewCount;
        private String representMenu;
        private Integer representMenuPrice;
        private String priceLevel;
        private String aiMateSummaryTitle;
        private List<String> aiMateSummaryContents;
        private TimeSlot timeSlot;
        private boolean skip;

        private static EnrichedRestaurantData fromSuggestion(SuggestionRestaurant suggestion) {
            EnrichedRestaurantData data = new EnrichedRestaurantData();
            data.rating = suggestion.rating();
            return data;
        }

        private static EnrichedRestaurantData skipped() {
            EnrichedRestaurantData data = new EnrichedRestaurantData();
            data.skip = true;
            return data;
        }

        private String externalId() {
            return skip ? null : externalId;
        }

        private String mapUrl() {
            return skip ? null : mapUrl;
        }

        private GeoJson.Point geoJsonLocation() {
            return skip ? null : geoJsonLocation;
        }

        private Double rating() {
            return skip ? null : rating;
        }

        private String imageUrl() {
            return skip ? null : imageUrl;
        }

        private String placeName() {
            return skip ? null : placeName;
        }

        private String representativeReview() {
            return skip ? null : representativeReview;
        }

        private Integer reviewCount() {
            return skip ? null : reviewCount;
        }

        private Integer blogReviewCount() {
            return skip ? null : blogReviewCount;
        }

        private String representMenu() {
            return skip ? null : representMenu;
        }

        private Integer representMenuPrice() {
            return skip ? null : representMenuPrice;
        }

        private String priceLevel() {
            return skip ? null : priceLevel;
        }

        private String aiMateSummaryTitle() {
            return skip ? null : aiMateSummaryTitle;
        }

        private List<String> aiMateSummaryContents() {
            return skip ? null : aiMateSummaryContents;
        }

        private TimeSlot timeSlot() {
            return skip ? null : timeSlot;
        }

        private boolean isSkipped() {
            return skip;
        }
    }
}
