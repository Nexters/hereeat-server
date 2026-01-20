package com.yogieat.external.ai.gemini;

import java.util.List;

public interface GeminiClient {
    List<RestaurantSuggestion> generateRestaurants(String region, String category, int count);
}
