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
    private static final long BATCH_DELAY_MS = 15000; // 배치 간 휴식 시간 (10초)

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
        restaurantValidator.prepareForBatchValidation(region);

        List<SuggestionRestaurant> suggestions = geminiClient.generateRestaurants(
            location, category, RESTAURANTS_PER_REQUEST
        );

        int savedCount = 0;
        for (SuggestionRestaurant suggestion : suggestions) {
            boolean saved = processRestaurant(suggestion, region, location);
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
        restaurantValidator.prepareForBatchValidation(region);

        // 3. 각 suggestion 처리 (카테고리 생성 + Gemini 데이터 + Kakao 데이터 보강)
        int savedCount = 0;
        for (SuggestionRestaurant suggestion : suggestions) {
            boolean saved = processRestaurant(suggestion, region, location);
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
    protected boolean processRestaurant(SuggestionRestaurant suggestion, Region restaurantRegion, String locationName) {
        try {
            // 1. largeCategory displayName을 LargeCategory enum으로 변환
            LargeCategory largeCategory = getLargeCategoryFromDisplayName(suggestion.largeCategory());

            // 2. Gemini suggestion으로부터 카테고리 조회 또는 생성
            Long categoryId = categoryService.findOrCreateCategory(
                largeCategory,
                suggestion.mediumCategory()
            );

            // 3. 데이터 보강용 변수 초기화
            String externalId = null;
            String mapUrl = null;
            GeoJson.Point geoJsonLocation = null;
            Double rating = suggestion.rating(); // Gemini 평점으로 시작
            String imageUrl = null;
            String placeName = null;
            String representativeReview = null;

            // 3-1. 추천 근거 데이터 변수 초기화
            Integer reviewCount = null;
            Integer blogReviewCount = null;
            String representMenu = null;
            Integer representMenuPrice = null;
            String priceLevel = null;
            String aiMateSummaryTitle = null;
            List<String> aiMateSummaryContents = null;
            // 추천 시간대
            TimeSlot timeSlot = null;

            // 4. Kakao Search API로 기본 정보 보강 시도
            Optional<KaKaoPlaceDocumentResult> placeOpt = kakaoPlaceClient.searchPlace(
                suggestion.name(), locationName
            );

            if (placeOpt.isPresent()) {
                KaKaoPlaceDocumentResult place = placeOpt.get();

                // 4-0. 카페/커피숍 필터링
                String categoryName = place.categoryName();
                if (categoryName != null &&
                    (categoryName.contains("카페") || categoryName.contains("커피"))) {
                    log.info("Skipping cafe/coffee shop: {} (category: {})",
                        suggestion.name(), categoryName);
                    return false;
                }

                // 4-1. Kakao 검색 데이터로 보강
                KakaoRestaurantData data = kakaoPlaceMapper.toDomainData(place);
                externalId = data.externalId();
                String originalMapUrl = data.mapUrl();
                if (originalMapUrl.startsWith("https:")) {
                    mapUrl = originalMapUrl;
                } else if (originalMapUrl.startsWith("http:")) {
                    mapUrl = "https:" + originalMapUrl.substring("http:".length());
                } else if (originalMapUrl.startsWith("//")) {
                    mapUrl = "https:" + originalMapUrl;
                } else {
                    mapUrl = originalMapUrl;
                }
                geoJsonLocation = data.location();

                // 5. Kakao Detail API (panel3)로 평점 및 사진 보강 시도
                Optional<KakaoPlaceDetailData> detailOpt = kakaoPlaceDetailClient.fetchPlaceDetail(place.id());
                if (detailOpt.isPresent()) {
                    KakaoPlaceDetailData detail = detailOpt.get();

                    // 5-1. 필터링 체크: rating이 null이면 음식점이 아니거나 평점이 범위 밖
                    if (detail.rating() == null) {
                        log.info("Skipping restaurant due to filtering (non-restaurant or invalid rating): {} in {}",
                            suggestion.name(), locationName);
                        return false;
                    }

                    // 5-2. panel3 평점 사용 (Gemini보다 정확)
                    rating = detail.rating();
                    placeName = detail.placeName();

                    // 5-3. 메인 사진 URL이 있으면 사용
                    if (detail.mainPhotoUrl() != null && !detail.mainPhotoUrl().isBlank()) {
                        imageUrl = detail.mainPhotoUrl();
                    }

                    // 5-4. 대표 리뷰가 있으면 사용
                    if (detail.representativeReview() != null && !detail.representativeReview().isBlank()) {
                        representativeReview = detail.representativeReview();
                    }

                    // 5-5. 추천 근거 데이터 추출
                    reviewCount = detail.reviewCount();
                    blogReviewCount = detail.blogReviewCount();
                    representMenu = detail.representMenu();
                    representMenuPrice = normalizeMenuPrice(detail.representMenuPrice());
                    priceLevel = detail.priceLevel();
                    aiMateSummaryTitle = detail.aiMateSummaryTitle();
                    aiMateSummaryContents = detail.aiMateSummaryContents();
                    // 추천 시간대 추출
                    timeSlot = detail.timeSlot();

                    // 5-6. ai_mate 데이터 필터링
                    if (aiMateSummaryTitle == null || aiMateSummaryTitle.isBlank()) {
                        log.info("Skipping restaurant due to missing ai_mate data: {}",
                            suggestion.name());
                        return false;
                    }

                    // 5-7. 리뷰 수 필터링: review_count >= 30 AND blog_review_count >= 30
                    if (reviewCount == null || reviewCount < 30 || blogReviewCount == null || blogReviewCount < 30) {
                        log.info("Skipping restaurant due to insufficient reviews: {} (reviews: {}, blog: {})",
                            suggestion.name(), reviewCount, blogReviewCount);
                        return false;
                    }
                }
            } else {
                log.warn("Kakao place not found for: {} in {}", suggestion.name(), locationName);
            }

            // 6. externalId가 null이면 저장하지 않음 (Kakao 데이터 필수)
            if (externalId == null || externalId.isBlank()) {
                log.warn("Skipping restaurant due to missing externalId: {} in {}",
                    suggestion.name(), locationName);
                return false;
            }

            // 7. 저장 전 중복 검증 (캐시 사용)
            RestaurantValidator.ValidationResult validationResult =
                restaurantValidator.duplicateValidateWithCache(suggestion, externalId);

            if (!validationResult.isValid()) {
                return false;
            }

            // 8. Gemini + Kakao 보강 데이터로 Restaurant 생성
            CreateRestaurant createRestaurant = CreateRestaurant.of(
                suggestion,
                placeName,
                categoryId,
                externalId,
                mapUrl,
                geoJsonLocation,
                rating,
                imageUrl,
                representativeReview,
                restaurantRegion,
                // 추천 근거 데이터
                reviewCount,
                blogReviewCount,
                representMenu,
                representMenuPrice,
                priceLevel,
                aiMateSummaryTitle,
                aiMateSummaryContents,
                // 추천 시간대
                timeSlot
            );

            // 9. 도메인 레포지토리를 통해 저장
            restaurantRepository.save(createRestaurant);

            // 10. 같은 배치 내 중복 방지를 위해 캐시에 추가
            restaurantValidator.addToCache(externalId, suggestion.name(), suggestion.address());

            return true;

        } catch (Exception e) {
            log.error("Failed to process restaurant: {} in {}", suggestion.name(), locationName, e);
            // 예외 발생 시 이 트랜잭션만 롤백 (REQUIRES_NEW)
            return false;
        }
    }

    private Integer normalizeMenuPrice(Integer price) {
        if (price == null || price <= 0) {
            return null;
        }
        return price;
    }
}
