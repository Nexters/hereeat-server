package com.yogieat.ai.gemini;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import com.yogieat.ai.gemini.config.GeminiProperties;
import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import com.yogieat.external.LocationCategoryKey;
import com.yogieat.restaurant.domain.SuggestionRestaurant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class GeminiClientImpl implements com.yogieat.external.ai.GeminiClient {

    private final GeminiProperties geminiProperties;
    private final GeminiPromptBuilder promptBuilder;
    private final GeminiResponseParser responseParser;

    @Override
    public List<SuggestionRestaurant> generateRestaurants(String location, String category, int count) {
        try {
            log.info("Calling Gemini API for location: {}, category: {}", location, category);

            // 1. API 키로 Gemini 클라이언트 생성
            Client client = Client.builder().apiKey(geminiProperties.apiKey()).build();

            // 2. 중앙화된 빌더를 사용하여 프롬프트 생성
            String prompt = promptBuilder.buildRestaurantGenerationPrompt(location, category, count);

            // 3. Gemini API 호출
            GenerateContentResponse response = client.models.generateContent(geminiProperties.model(), prompt, null);

            String responseText = response.text();
            log.debug("Gemini API response: {}", responseText);

            // 4. 중앙화된 파서를 사용하여 응답 파싱
            return responseParser.parseSuggestionRestaurants(responseText);

        } catch (CustomException e) {
            log.error("Custom exception during Gemini API call for location: {}, category: {}", location, category, e);
            throw e;
        } catch (Exception e) {
            log.error("Gemini API call failed for location: {}, category: {}", location, category, e);
            throw new CustomException(ErrorCode.GEMINI_API_ERROR);
        }
    }

    @Override
    public Map<LocationCategoryKey, List<SuggestionRestaurant>> generateRestaurantsBatch(
        List<String> locations,
        List<String> categories,
        String restaurantNames,
        int countPerCombo
    ) {
        long startTime = System.currentTimeMillis();
        try {
            log.info("Calling Gemini API batch for {} locations × {} categories = {} combinations",
                locations.size(), categories.size(), locations.size() * categories.size());

            // 1. API 키로 Gemini 클라이언트 생성
            log.debug("Creating Gemini client...");
            Client client = Client.builder().apiKey(geminiProperties.apiKey()).build();
            log.debug("Gemini client created successfully");

            // 2. 중앙화된 빌더를 사용하여 배치 프롬프트 생성
            log.debug("Building batch prompt...");
            String prompt = promptBuilder.buildBatchRestaurantGenerationPrompt(locations, categories, restaurantNames, countPerCombo);
            log.info("Batch prompt generated: {} characters, {} locations, {} categories",
                prompt.length(), locations, categories);

            // 3. Gemini API 호출 (모든 조합을 단일 호출로 처리)
            log.info("Calling Gemini API with model: {}...", geminiProperties.model());
            log.warn("This may take a long time for batch requests. Please wait...");

            GenerateContentResponse response = client.models.generateContent(geminiProperties.model(), prompt, null);

            long apiCallDuration = System.currentTimeMillis() - startTime;
            log.info("Gemini API call completed in {} ms ({} seconds)", apiCallDuration, apiCallDuration / 1000);

            String responseText = response.text();
            if (responseText == null) {
                log.error("Gemini API returned null response text");
                throw new CustomException(ErrorCode.GEMINI_API_ERROR);
            }

            log.info("Gemini API batch response received: {} characters", responseText.length());
            log.debug("Response preview (first 500 chars): {}",
                responseText.length() > 500 ? responseText.substring(0, 500) + "..." : responseText);

            // 4. 중앙화된 파서를 사용하여 응답 파싱
            log.debug("Parsing batch response...");
            Map<LocationCategoryKey, List<SuggestionRestaurant>> result =
                responseParser.parseBatchSuggestionRestaurants(responseText);

            long totalDuration = System.currentTimeMillis() - startTime;
            log.info("Batch API call succeeded: {} location-category combinations processed in {} ms ({} seconds)",
                result.size(), totalDuration, totalDuration / 1000);

            return result;

        } catch (CustomException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Custom exception during batch Gemini API call after {} ms: {}", duration, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Batch Gemini API call failed after {} ms: {} - {}",
                duration, e.getClass().getSimpleName(), e.getMessage(), e);
            throw new CustomException(ErrorCode.GEMINI_API_ERROR);
        }
    }
}
