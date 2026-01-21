package com.yogieat.domain.restaurant.service;

import com.yogieat.domain.category.service.CategoryService;
import com.yogieat.domain.common.GeoJson;
import com.yogieat.domain.gathering.domain.value.Place;
import com.yogieat.domain.restaurant.domain.CreateRestaurant;
import com.yogieat.external.ai.gemini.GeminiClient;
import com.yogieat.external.ai.gemini.RestaurantSuggestion;
import com.yogieat.external.kakao.map.KaKaoPlaceDocument;
import com.yogieat.external.kakao.map.KakaoPlaceClient;
import com.yogieat.external.kakao.map.KakaoPlaceDetailClient;
import com.yogieat.external.kakao.map.KakaoPlaceDetailData;
import com.yogieat.external.kakao.map.KakaoPlaceMapper;
import com.yogieat.external.kakao.map.KakaoRestaurantData;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RestaurantCollectionService {

    private final GeminiClient geminiClient;
    private final KakaoPlaceClient kakaoPlaceClient;
    private final KakaoPlaceDetailClient kakaoPlaceDetailClient;
    private final KakaoPlaceMapper kakaoPlaceMapper;
    private final RestaurantRepository restaurantRepository;
    private final CategoryService categoryService;
    private final RestaurantValidator restaurantValidator;

    /**
     * Get location names from Place enum
     * Uses Place enum as the source of truth for supported locations
     */
    private static final List<String> LOCATIONS = Arrays.stream(Place.values())
        .map(Place::getName)
        .toList();

    private static final List<String> FOOD_CATEGORIES = List.of(
        "한식", "일식", "중식", "양식"
    );

    private static final int RESTAURANTS_PER_REQUEST = 20;
    private static final long RATE_LIMIT_DELAY_MS = 5000; // 5 seconds

    /**
     * Collect restaurants for all locations defined in Place enum
     */
    public void collectAllRegions() {
        log.info("Starting restaurant collection for all locations: {}", LOCATIONS);

        int totalProcessed = 0;
        int totalSuccess = 0;
        int totalFailed = 0;

        for (String location : LOCATIONS) {
            for (String category : FOOD_CATEGORIES) {
                try {
                    log.info("Processing location: {}, category: {}", location, category);
                    int processed = collectRestaurantsForLocation(location, category);
                    totalProcessed += processed;
                    totalSuccess++;

                    // Rate limit handling: sleep after each Gemini API call
                    Thread.sleep(RATE_LIMIT_DELAY_MS);

                } catch (Exception e) {
                    log.error("Failed to collect restaurants for location: {}, category: {}",
                        location, category, e);
                    totalFailed++;
                    // Continue with next location/category (partial failure handling)
                }
            }
        }

        log.info("Restaurant collection completed. Total: {}, Success: {}, Failed: {}, Restaurants: {}",
            LOCATIONS.size() * FOOD_CATEGORIES.size(), totalSuccess, totalFailed, totalProcessed);
    }

    /**
     * Collect restaurants for a specific location and category
     *
     * Data collection strategy:
     * 1. Generate restaurant data using Gemini AI (name, address, rating, category, description, review)
     * 2. Create or find category from Gemini-generated data (largeCategory, mediumCategory)
     * 3. Optionally enrich with Kakao Place API (location, mapUrl, externalId)
     * 4. Save all generated restaurants (even if Kakao enrichment fails)
     *
     * @param location Location name (from Place enum)
     * @param category Food category for Gemini prompt (large category hint)
     * @return Number of successfully saved restaurants
     */
    @Transactional
    public int collectRestaurantsForLocation(String location, String category) {
        log.info("Collecting restaurants for: {} - {}", location, category);

        // 1. Call Gemini API to generate restaurant suggestions with full data (including category info)
        List<RestaurantSuggestion> suggestions = geminiClient.generateRestaurants(
            location, category, RESTAURANTS_PER_REQUEST
        );

        log.info("Gemini generated {} suggestions for {} - {}", suggestions.size(), location, category);

        // 2. Process each suggestion (category creation + Gemini data + optional Kakao enrichment)
        int savedCount = 0;
        for (RestaurantSuggestion suggestion : suggestions) {
            boolean saved = processRestaurant(suggestion, location);
            if (saved) {
                savedCount++;
            }
        }

        log.info("Saved {}/{} restaurants for {} - {}",
            savedCount, suggestions.size(), location, category);

        return savedCount;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected boolean processRestaurant(RestaurantSuggestion suggestion, String location) {
        try {
            // 1. Find or create category from Gemini suggestion
            Long categoryId = categoryService.findOrCreateCategory(
                suggestion.largeCategory(),
                suggestion.mediumCategory()
            );

            // 2. Initialize enrichment variables
            String externalId = null;
            String mapUrl = null;
            GeoJson.Point geoJsonLocation = null;
            Double rating = suggestion.rating(); // Start with Gemini rating
            String imageUrl = null;

            // 3. Try to enrich with Kakao Search API data (basic info)
            Optional<KaKaoPlaceDocument> placeOpt = kakaoPlaceClient.searchPlace(
                suggestion.name(), location
            );

            if (placeOpt.isPresent()) {
                KaKaoPlaceDocument place = placeOpt.get();

                // Enrich with Kakao search data
                KakaoRestaurantData data = kakaoPlaceMapper.toDomainData(place);
                externalId = data.externalId();
                mapUrl = data.mapUrl();
                geoJsonLocation = new GeoJson.Point(
                    List.of(data.location().getX(), data.location().getY())
                );

                log.debug("Enriched restaurant with Kakao search data: {} ({})", place.placeName(), place.id());

                // 4. Try to enrich with Kakao Detail API (panel3) for rating and photos
                Optional<KakaoPlaceDetailData> detailOpt = kakaoPlaceDetailClient.fetchPlaceDetail(place.id());
                if (detailOpt.isPresent()) {
                    KakaoPlaceDetailData detail = detailOpt.get();

                    // Use panel3 rating if available (more accurate than Gemini)
                    if (detail.rating() != null && detail.rating() > 0) {
                        rating = detail.rating();
                        log.debug("Updated rating from panel3: {}", rating);
                    }

                    // Use main photo URL if available
                    if (detail.mainPhotoUrl() != null && !detail.mainPhotoUrl().isBlank()) {
                        imageUrl = detail.mainPhotoUrl();
                        log.debug("Added image URL from panel3: {}", imageUrl);
                    }
                } else {
                    log.debug("panel3 data not available for placeId: {}", place.id());
                }
            } else {
                log.info("Kakao place not found, saving with Gemini data only: {} in {}",
                    suggestion.name(), location);
            }

            // 5. Validate for duplicates before saving
            RestaurantValidator.ValidationResult validationResult =
                restaurantValidator.duplicateValidate(suggestion, externalId);

            if (!validationResult.isValid()) {
                if (validationResult.isDuplicate()) {
                    log.debug("Duplicate restaurant detected: {} - {}",
                        suggestion.name(), validationResult.reason());
                } else {
                    log.warn("Invalid restaurant data: {} - {}",
                        suggestion.name(), validationResult.reason());
                }
                return false;
            }

            // 6. Create Restaurant using static factory method with Gemini + Kakao enrichment
            CreateRestaurant createRestaurant = CreateRestaurant.of(
                suggestion,
                categoryId,
                externalId,
                mapUrl,
                geoJsonLocation,
                rating,
                imageUrl
            );

            // 7. Save through domain repository
            restaurantRepository.save(createRestaurant);

            log.info("Saved new restaurant: {} (Category: {}/{}, Rating: {}, Image: {})",
                suggestion.name(), suggestion.largeCategory(), suggestion.mediumCategory(),
                rating, imageUrl != null ? "Yes" : "No");
            return true;

        } catch (Exception e) {
            log.error("Failed to process restaurant: {} in {}", suggestion.name(), location, e);
            // Exception will rollback only this transaction (REQUIRES_NEW)
            return false;
        }
    }
}
