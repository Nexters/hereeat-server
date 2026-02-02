package com.yogieat.restaurant.domain;

import com.yogieat.common.GeoJson;
import com.yogieat.common.Region;

/**
 * DTO for creating a new Restaurant
 * Separates creation concerns from the domain entity
 */
public record CreateRestaurant(
        String externalId,
        Long categoryId,
        String name,
        String address,
        Double rating,
        String imageUrl,
        String mapUrl,
        String representativeReview,
        String description,
        Region region,
        GeoJson.Point location
) {
    public static CreateRestaurant of(
            SuggestionRestaurant suggestion,
            String placeName,
            Long categoryId,
            String externalId,
            String mapUrl,
            GeoJson.Point location,
            Double rating,
            String imageUrl,
            String representativeReview,
            Region region
    ) {
        return new CreateRestaurant(
                externalId,
                categoryId,
                placeName,
                suggestion.address(),
                rating,  // Use enriched rating if available
                imageUrl,  // Use Kakao image if available
                mapUrl,
                representativeReview != null ? representativeReview : suggestion.representativeReview(),  // Use Kakao review if available
                suggestion.description(),
			region,
                location
        );
    }
}
