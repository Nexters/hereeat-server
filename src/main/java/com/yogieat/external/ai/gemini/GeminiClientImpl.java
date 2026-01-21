package com.yogieat.external.ai.gemini;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import com.yogieat.global.config.ai.GeminiProperties;
import com.yogieat.global.error.CustomException;
import com.yogieat.global.error.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class GeminiClientImpl implements GeminiClient {

    private final GeminiProperties geminiProperties;
    private final GeminiPromptBuilder promptBuilder;
    private final GeminiResponseParser responseParser;

    @Override
    public List<RestaurantSuggestion> generateRestaurants(String location, String category, int count) {
        try {
            log.info("Calling Gemini API for location: {}, category: {}", location, category);

            // Create Gemini client with API key
            Client client = Client.builder().apiKey(geminiProperties.apiKey()).build();

            // Build prompt using centralized builder
            String prompt = promptBuilder.buildRestaurantGenerationPrompt(location, category, count);

            // Call Gemini API
            GenerateContentResponse response = client.models.generateContent(geminiProperties.model(), prompt, null);

            String responseText = response.text();
            log.debug("Gemini API response: {}", responseText);

            // Parse response using centralized parser
            return responseParser.parseRestaurantSuggestions(responseText);

        } catch (CustomException e) {
            log.error("Custom exception during Gemini API call for location: {}, category: {}", location, category, e);
            throw e;
        } catch (Exception e) {
            log.error("Gemini API call failed for location: {}, category: {}", location, category, e);
            throw new CustomException(ErrorCode.GEMINI_API_ERROR);
        }
    }
}
