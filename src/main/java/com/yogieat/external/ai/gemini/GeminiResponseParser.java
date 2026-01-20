package com.yogieat.external.ai.gemini;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yogieat.global.error.CustomException;
import com.yogieat.global.error.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Parser for Gemini API responses
 * Handles JSON extraction and deserialization with error handling
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GeminiResponseParser {

    private final ObjectMapper objectMapper;

    /**
     * Parse Gemini response text to list of RestaurantSuggestion
     * Handles markdown code blocks and malformed JSON
     *
     * @param responseText Raw response from Gemini API
     * @return List of parsed restaurant suggestions
     * @throws CustomException if parsing fails
     */
    public List<RestaurantSuggestion> parseRestaurantSuggestions(String responseText) {
        try {
            String cleanedJson = cleanJsonResponse(responseText);

            List<RestaurantSuggestion> suggestions = objectMapper.readValue(
                cleanedJson,
                new TypeReference<List<RestaurantSuggestion>>() {}
            );

            log.info("Successfully parsed {} restaurant suggestions", suggestions.size());
            return suggestions;

        } catch (Exception e) {
            log.error("Failed to parse Gemini response: {}", responseText, e);
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
