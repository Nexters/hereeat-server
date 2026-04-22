package com.yogieat.restaurant.service;

import com.yogieat.category.domain.value.LargeCategory;
import com.yogieat.category.service.CategoryService;
import com.yogieat.common.Region;
import com.yogieat.region.domain.RegionMaster;
import com.yogieat.restaurant.domain.CreateRestaurant;
import com.yogieat.restaurant.domain.SuggestionRestaurant;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RestaurantCollectionWriteService {

    private static final Logger log = LoggerFactory.getLogger(RestaurantCollectionWriteService.class);

    private final CategoryService categoryService;
    private final RestaurantRepository restaurantRepository;
    private final RestaurantValidator restaurantValidator;

    public RestaurantValidator.ValidationContext prepareValidationContext(RegionMaster region) {
        return restaurantValidator.prepareForBatchValidation(region.id(), region.displayName());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean persistRestaurant(
            SuggestionRestaurant suggestion,
            RegionMaster restaurantRegion,
            LargeCategory largeCategory,
            String mediumCategory,
            RestaurantEnrichedData enrichedData,
            RestaurantValidator.ValidationContext validationContext
    ) {
        try {
            if (enrichedData.externalId() == null || enrichedData.externalId().isBlank()) {
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
                    Region.fromString(restaurantRegion.code()),
                    enrichedData.reviewCount(),
                    enrichedData.blogReviewCount(),
                    enrichedData.representMenu(),
                    enrichedData.representMenuPrice(),
                    enrichedData.priceLevel(),
                    enrichedData.aiMateSummaryTitle(),
                    enrichedData.aiMateSummaryContents(),
                    enrichedData.timeSlot(),
                    enrichedData.offDays()
            );

            restaurantRepository.save(createRestaurant, restaurantRegion.id());
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
}
