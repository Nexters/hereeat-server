package com.yogieat.restaurant.service;

import com.yogieat.category.domain.value.LargeCategory;
import com.yogieat.category.service.CategoryService;
import com.yogieat.common.GeoJson;
import com.yogieat.common.Region;
import com.yogieat.restaurant.domain.CreateRestaurant;
import com.yogieat.restaurant.domain.SuggestionRestaurant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RestaurantCollectionWriteService {

    private static final double COLLECTION_REGION_RADIUS_KM = 1.0;
    private final CategoryService categoryService;
    private final RestaurantRepository restaurantRepository;
    private final RestaurantValidator restaurantValidator;

    public RestaurantValidator.ValidationContext prepareValidationContext(Region region) {
        return restaurantValidator.prepareForBatchValidation(region);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean persistRestaurant(
            SuggestionRestaurant suggestion,
            Region restaurantRegion,
            LargeCategory largeCategory,
            String mediumCategory,
            RestaurantEnrichedData enrichedData,
            RestaurantValidator.ValidationContext validationContext
    ) {
        try {
            if (enrichedData.externalId() == null || enrichedData.externalId().isBlank()) {
                return false;
            }

            if (!isWithinRegionRadius(restaurantRegion, enrichedData.geoJsonLocation())) {
                log.info("Skipping restaurant outside region radius: {} ({})",
                        suggestion.name(),
                        restaurantRegion.getName()
                );
                return false;
            }

            Long categoryId = categoryService.findOrCreateCategory(largeCategory, mediumCategory);
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
            log.error("Failed to persist restaurant: {}", suggestion.name(), e);
            return false;
        }
    }

    private boolean isWithinRegionRadius(Region region, GeoJson.Point restaurantPoint) {
        if (region == null || region.getCoordinatesStandard() == null) {
            return true;
        }

        if (!isValidPoint(restaurantPoint)) {
            return true;
        }

        return calculateDistanceKm(region.getCoordinatesStandard(), restaurantPoint) <= COLLECTION_REGION_RADIUS_KM;
    }

    private boolean isValidPoint(GeoJson.Point point) {
        return point != null
                && point.getCoordinates() != null
                && point.getCoordinates().size() >= 2
                && point.getCoordinates().get(0) != null
                && point.getCoordinates().get(1) != null;
    }

    private double calculateDistanceKm(GeoJson.Point centerPoint, GeoJson.Point targetPoint) {
        double lat1 = centerPoint.getCoordinates().get(1);
        double lon1 = centerPoint.getCoordinates().get(0);
        double lat2 = targetPoint.getCoordinates().get(1);
        double lon2 = targetPoint.getCoordinates().get(0);

        double r = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return r * c;
    }
}
