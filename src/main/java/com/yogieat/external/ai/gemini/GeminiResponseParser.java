package com.yogieat.external.ai.gemini;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yogieat.domain.restaurant.domain.SuggestionRestaurant;
import com.yogieat.global.error.CustomException;
import com.yogieat.global.error.ErrorCode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class GeminiResponseParser {

    private final ObjectMapper objectMapper;

    /**
     * Parse Gemini response text to list of SuggestionRestaurant
     * Handles markdown code blocks and malformed JSON
     *
     * @param responseText Raw response from Gemini API
     * @return List of parsed restaurant suggestions
     * @throws CustomException if parsing fails
     */
    public List<SuggestionRestaurant> parseSuggestionRestaurants(String responseText) {
        try {
            String cleanedJson = cleanJsonResponse(responseText);

            List<SuggestionRestaurant> suggestions = objectMapper.readValue(
                cleanedJson,
                new TypeReference<List<SuggestionRestaurant>>() {}
            );

            log.info("Successfully parsed {} restaurant suggestions", suggestions.size());
            return suggestions;

        } catch (Exception e) {
            log.error("Failed to parse Gemini response: {}", responseText, e);
            throw new CustomException(ErrorCode.GEMINI_RESPONSE_PARSE_ERROR);
        }
    }

    /**
     * Parse batch Gemini response to map of location-category combinations
     * Handles nested JSON structure: { "location": { "category": [...] } }
     *
     * @param responseText Raw response from Gemini API
     * @return Map of LocationCategoryKey to list of restaurant suggestions
     * @throws CustomException if parsing fails
     */
    public Map<LocationCategoryKey, List<SuggestionRestaurant>> parseBatchSuggestionRestaurants(String responseText) {
        try {
            String cleanedJson = cleanJsonResponse(responseText);

            // Parse nested structure: Map<Location, Map<Category, List<SuggestionRestaurant>>>
            Map<String, Map<String, List<SuggestionRestaurant>>> nestedMap = objectMapper.readValue(
                cleanedJson,
                new TypeReference<Map<String, Map<String, List<SuggestionRestaurant>>>>() {}
            );

            // Flatten to Map<LocationCategoryKey, List<SuggestionRestaurant>>
            Map<LocationCategoryKey, List<SuggestionRestaurant>> result = new HashMap<>();
            int totalCount = 0;

            for (Map.Entry<String, Map<String, List<SuggestionRestaurant>>> locationEntry : nestedMap.entrySet()) {
                String location = locationEntry.getKey();
                Map<String, List<SuggestionRestaurant>> categoryMap = locationEntry.getValue();

                for (Map.Entry<String, List<SuggestionRestaurant>> categoryEntry : categoryMap.entrySet()) {
                    String category = categoryEntry.getKey();
                    List<SuggestionRestaurant> suggestions = categoryEntry.getValue();

                    LocationCategoryKey key = new LocationCategoryKey(location, category);
                    result.put(key, suggestions);
                    totalCount += suggestions.size();
                }
            }

            log.info("Successfully parsed batch response: {} location-category combinations, {} total restaurants",
                result.size(), totalCount);
            return result;

        } catch (Exception e) {
            log.error("Failed to parse batch Gemini response: {}", responseText, e);
            throw new CustomException(ErrorCode.GEMINI_RESPONSE_PARSE_ERROR);
        }
    }

    /**
     * Clean JSON response by removing markdown code blocks
     *
     * @param jsonResponse Raw JSON response (may contain ```json or ``` markers)
     * @return Cleaned JSON string
     */
    private String cleanJsonResponse(String jsonResponse) {
        String cleaned = jsonResponse.trim();

        // Remove ```json opening
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        }
        // Remove ``` opening
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        // Remove ``` closing
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        return cleaned.trim();
    }
}
