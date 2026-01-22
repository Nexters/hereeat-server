package com.yogieat.external.ai.gemini;

import com.yogieat.domain.restaurant.domain.SuggestionRestaurant;
import java.util.List;
import java.util.Map;

public interface GeminiClient {
    List<SuggestionRestaurant> generateRestaurants(String region, String category, int count);

    /**
     * Generate restaurants for multiple location-category combinations in a single API call
     * Reduces API calls from N to 1, improving performance and cost efficiency
     *
     * @param locations List of location names (e.g., ["홍대입구역", "강남역"])
     * @param categories List of food categories (e.g., ["한식", "중식", "일식"])
     * @param countPerCombo Number of restaurants per location-category combination
     * @return Map of LocationCategoryKey to list of restaurant suggestions
     */
    Map<LocationCategoryKey, List<SuggestionRestaurant>> generateRestaurantsBatch(
        List<String> locations,
        List<String> categories,
        int countPerCombo
    );
}
