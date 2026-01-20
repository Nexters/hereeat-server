package com.yogieat.domain.restaurant.service;

import com.yogieat.domain.category.service.CategoryService;
import com.yogieat.domain.common.GeoJson;
import com.yogieat.domain.gathering.domain.value.Place;
import com.yogieat.domain.restaurant.domain.Restaurant;
import com.yogieat.external.ai.gemini.GeminiClient;
import com.yogieat.external.ai.gemini.RestaurantSuggestion;
import com.yogieat.external.kakao.map.KaKaoPlaceDocument;
import com.yogieat.external.kakao.map.KakaoPlaceClient;
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
    private final KakaoPlaceMapper kakaoPlaceMapper;
    private final RestaurantRepository restaurantRepository;
    private final CategoryService categoryService;

    /**
     * Get location names from Place enum
     * Uses Place enum as the source of truth for supported locations
     */
    private static final List<String> LOCATIONS = Arrays.stream(Place.values())
        .map(Place::getName)
        .toList();

    private static final List<String> FOOD_CATEGORIES = List.of(
        "한식", "일식", "중식", "양식", "카페"
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
     * @param location Location name (from Place enum)
     * @param category Food category
     * @return Number of successfully saved restaurants
     */
    @Transactional
    public int collectRestaurantsForLocation(String location, String category) {
        log.info("Collecting restaurants for: {} - {}", location, category);

        // 1. Find or create category
        Long categoryId = categoryService.findOrCreateCategory(category, null);

        // 2. Call Gemini API to generate restaurant suggestions
        List<RestaurantSuggestion> suggestions = geminiClient.generateRestaurants(
            location, category, RESTAURANTS_PER_REQUEST
        );

        log.info("Gemini generated {} suggestions for {} - {}", suggestions.size(), location, category);

        // 3. Process each suggestion
        int savedCount = 0;
        for (RestaurantSuggestion suggestion : suggestions) {
            boolean saved = processRestaurant(suggestion, location, categoryId);
            if (saved) {
                savedCount++;
            }
        }

        log.info("Saved {}/{} restaurants for {} - {}",
            savedCount, suggestions.size(), location, category);

        return savedCount;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected boolean processRestaurant(RestaurantSuggestion suggestion, String location, Long categoryId) {
        try {
            // 4. Search Kakao API for validation
            Optional<KaKaoPlaceDocument> placeOpt = kakaoPlaceClient.searchPlace(
                suggestion.name(), location
            );

            if (placeOpt.isEmpty()) {
                log.warn("Kakao place not found (hallucination): {} in {}",
                    suggestion.name(), location);
                return false;
            }

            KaKaoPlaceDocument place = placeOpt.get();

            // 5. Check duplicate by externalId
            if (restaurantRepository.existsByExternalId(place.id())) {
                log.debug("Restaurant already exists (duplicate): {}", place.placeName());
                return false;
            }

            // 6. Convert to domain data (Clean Architecture)
            // External layer (kakaoPlaceMapper) → Domain DTO (no JPA dependency)
            KakaoRestaurantData data = kakaoPlaceMapper.toDomainData(place);

            // 7. Create Restaurant domain object
            // Convert JTS Point to GeoJson.Point for domain layer
            GeoJson.Point geoJsonLocation = new GeoJson.Point(
                List.of(data.location().getX(), data.location().getY())
            );

            Restaurant restaurant = new Restaurant(
                null,  // id will be generated
                data.externalId(),
                categoryId,
                data.name(),
                data.address(),
                null,  // rating - to be added later
                null,  // imageUrl - to be added later
                data.mapUrl(),
                null,  // representativeReview - to be added later
                null,  // description - to be added later
                geoJsonLocation
            );

            // 8. Save through domain repository (no JPA dependency)
            restaurantRepository.save(restaurant);

            log.info("Saved new restaurant: {} ({})", place.placeName(), place.id());
            return true;

        } catch (Exception e) {
            log.error("Failed to process restaurant: {} in {}", suggestion.name(), location, e);
            // Exception will rollback only this transaction (REQUIRES_NEW)
            return false;
        }
    }
}
