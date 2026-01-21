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
            "address": "%s 근처 상세주소",
            "rating": 4.5,
            "largeCategory": "대카테고리 (예: 한식, 일식, 중식, 양식, 카페)",
            "mediumCategory": "중카테고리 (예: 파스타, 스테이크, 초밥, 라멘, 해산물 등 구체적인 음식 종류)",
            "description": "식당의 특징, 분위기, 대표메뉴 등을 포함한 상세 설명 (2-3문장)",
            "representativeReview": "실제 방문객이 남길 법한 대표 리뷰 (1-2문장)"
          }
        ]

        각 식당에 대해:
        1. 실제로 존재하는 유명한 식당을 추천해주세요
        2. rating은 4.0~5.0 사이의 적절한 평점을 부여하세요 (소수점 첫째자리까지)
        3. largeCategory는 "%s"를 기반으로 설정하세요
        4. mediumCategory는 해당 식당의 구체적인 음식 종류를 지정하세요
        5. description은 식당의 특징과 인기메뉴를 구체적으로 설명해주세요
        6. representativeReview는 긍정적이고 구체적인 경험을 담은 리뷰를 작성해주세요

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
            location,
            category
        );
    }
}
