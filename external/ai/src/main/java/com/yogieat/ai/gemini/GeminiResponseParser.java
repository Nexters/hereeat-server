package com.yogieat.ai.gemini;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import com.yogieat.external.LocationCategoryKey;
import com.yogieat.restaurant.domain.SuggestionRestaurant;
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
     * Gemini 응답 텍스트를 SuggestionRestaurant 리스트로 파싱
     * 마크다운 코드 블록과 잘못된 JSON 처리
     *
     * @param responseText Gemini API로부터의 원시 응답
     * @return 파싱된 맛집 제안 리스트
     * @throws CustomException 파싱 실패 시
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
     * 배치 Gemini 응답을 장소-카테고리 조합의 맵으로 파싱
     * 중첩된 JSON 구조 처리: { "location": { "category": [...] } }
     * 잘린 JSON 응답도 복구 시도
     *
     * @param responseText Gemini API로부터의 원시 응답
     * @return LocationCategoryKey에 대한 맛집 제안 리스트의 맵
     * @throws CustomException 파싱 실패 시
     */
    public Map<LocationCategoryKey, List<SuggestionRestaurant>> parseBatchSuggestionRestaurants(String responseText) {
        try {
            String cleanedJson = cleanJsonResponse(responseText);

            // 1. 먼저 그대로 파싱 시도
            Map<String, Map<String, List<SuggestionRestaurant>>> nestedMap;
            try {
                nestedMap = objectMapper.readValue(
                    cleanedJson,
                    new TypeReference<Map<String, Map<String, List<SuggestionRestaurant>>>>() {}
                );
            } catch (Exception parseError) {
                // 2. 파싱 실패 시 잘린 JSON 복구 시도
                log.warn("Initial parse failed, attempting to repair truncated JSON...");
                String repairedJson = repairTruncatedJson(cleanedJson);

                nestedMap = objectMapper.readValue(
                    repairedJson,
                    new TypeReference<Map<String, Map<String, List<SuggestionRestaurant>>>>() {}
                );
                log.info("Successfully parsed repaired JSON");
            }

            // 3. Map<LocationCategoryKey, List<SuggestionRestaurant>>로 평탄화
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
     * 마크다운 코드 블록을 제거하여 JSON 응답 정리
     *
     * @param jsonResponse 원시 JSON 응답 (```json 또는 ``` 마커를 포함할 수 있음)
     * @return 정리된 JSON 문자열
     */
    private String cleanJsonResponse(String jsonResponse) {
        String cleaned = jsonResponse.trim();

        // ```json 시작 부분 제거
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        }
        // ``` 시작 부분 제거
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        // ``` 종료 부분 제거
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        return cleaned.trim();
    }


    /**
     * 잘린 JSON 응답을 복구 시도
     * LLM 응답이 토큰 제한으로 중간에 잘렸을 때 유효한 JSON으로 복구
     *
     * @param truncatedJson 잘린 JSON 문자열
     * @return 복구된 JSON 문자열
     */
    private String repairTruncatedJson(String truncatedJson) {
        if (truncatedJson == null || truncatedJson.isBlank()) {
            return "{}";
        }

        StringBuilder sb = new StringBuilder(truncatedJson);

        // 1. 마지막 불완전한 객체/배열 항목 제거
        // 마지막으로 완전한 객체가 끝나는 위치 찾기 (}, ] 경계)
        int lastCompleteIndex = findLastCompleteIndex(sb.toString());
        if (lastCompleteIndex > 0 && lastCompleteIndex < sb.length()) {
            sb.setLength(lastCompleteIndex);
        }

        // 2. 열린 괄호 개수 세기
        int openBraces = 0;   // {
        int openBrackets = 0; // [

        for (int i = 0; i < sb.length(); i++) {
            char c = sb.charAt(i);
            if (c == '{') openBraces++;
            else if (c == '}') openBraces--;
            else if (c == '[') openBrackets++;
            else if (c == ']') openBrackets--;
        }

        // 3. 닫히지 않은 괄호 닫기
        while (openBrackets > 0) {
            sb.append(']');
            openBrackets--;
        }
        while (openBraces > 0) {
            sb.append('}');
            openBraces--;
        }

        String repaired = sb.toString();
        log.debug("Repaired JSON: added {} closing braces, {} closing brackets",
                openBraces, openBrackets);

        return repaired;
    }

    /**
     * 마지막으로 완전한 JSON 요소가 끝나는 인덱스 찾기
     */
    private int findLastCompleteIndex(String json) {
        int lastComplete = -1;
        boolean inString = false;
        char prevChar = 0;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            // 문자열 내부인지 추적 (이스케이프된 따옴표 처리)
            if (c == '"' && prevChar != '\\') {
                inString = !inString;
            }

            // 문자열 외부에서 완전한 요소 종료 지점 찾기
            if (!inString) {
                if (c == '}' || c == ']') {
                    lastComplete = i + 1;
                }
            }

            prevChar = c;
        }

        return lastComplete;
    }
}
