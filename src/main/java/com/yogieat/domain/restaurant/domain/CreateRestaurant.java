package com.yogieat.domain.restaurant.domain;

import com.yogieat.domain.common.GeoJson;
import com.yogieat.external.ai.gemini.RestaurantSuggestion;

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
        GeoJson.Point location
) {
    /**
     * Create a CreateRestaurant from Gemini suggestion with optional Kakao enrichment
     *
     * @param suggestion Gemini-generated restaurant suggestion
     * @param categoryId Category ID
     * @param externalId Kakao place ID (nullable)
     * @param mapUrl Kakao map URL (nullable)
     * @param location GPS coordinates from Kakao (nullable)
     * @param rating Final rating (Gemini or enriched from Kakao panel3)
     * @param imageUrl Main image URL from Kakao panel3 (nullable)
     * @return CreateRestaurant instance
     */
    public static CreateRestaurant of(
            RestaurantSuggestion suggestion,
            Long categoryId,
            String externalId,
            String mapUrl,
            GeoJson.Point location,
            Double rating,
            String imageUrl
    ) {
        return new CreateRestaurant(
                externalId,
                categoryId,
                suggestion.name(),
                suggestion.address(),
                rating,  // Use enriched rating if available
                imageUrl,  // Use Kakao image if available
                mapUrl,
                suggestion.representativeReview(),
                suggestion.description(),
                location
        );
    }
}
