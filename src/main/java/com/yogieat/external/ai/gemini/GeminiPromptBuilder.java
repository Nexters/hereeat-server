package com.yogieat.external.ai.gemini;

import org.springframework.stereotype.Component;

/**
 * Builder for Gemini API prompts
 * Centralizes prompt templates for consistency and maintainability
 */
@Component
public class GeminiPromptBuilder {

    private static final String RESTAURANT_GENERATION_TEMPLATE =
        """
        %s 근처의 인기 %s %d개를 추천해주세요.

        다음 JSON 형식으로만 응답해주세요:
        [
          {
            "name": "식당명",
            "address": "%s 근처 상세주소"
          }
        ]

        설명 없이 JSON 배열만 반환하세요.
        """;

    /**
     * Build a prompt to generate restaurant recommendations
     *
     * @param location Location name (e.g., "홍대입구역", "강남역")
     * @param category Food category (e.g., "한식", "일식")
     * @param count Number of restaurants to generate
     * @return Formatted prompt string
     */
    public String buildRestaurantGenerationPrompt(String location, String category, int count) {
        return String.format(
            RESTAURANT_GENERATION_TEMPLATE,
            location,
            category,
            count,
            location
        );
    }
}
