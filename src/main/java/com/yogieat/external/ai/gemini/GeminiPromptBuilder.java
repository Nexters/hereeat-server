package com.yogieat.external.ai.gemini;

import com.yogieat.domain.category.domain.value.LargeCategory;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Gemini API 프롬프트 빌더
 * 일관성과 유지보수성을 위해 프롬프트 템플릿을 중앙화
 */
@Component
public class GeminiPromptBuilder {

    /**
     * LargeCategory enum의 displayName을 join한 문자열
     * 프롬프트에 동적으로 사용 (예: "한식, 중식, 일식, 양식, 아시안")
     */
    private static final String LARGE_CATEGORY_EXAMPLES = Arrays.stream(LargeCategory.values())
        .map(LargeCategory::getDisplayName)
        .collect(Collectors.joining(", "));

    private static final String RESTAURANT_GENERATION_TEMPLATE = String.format(
        """
        %%s 근처의 인기 %%s %%d개를 추천해주세요.

        다음 JSON 형식으로만 응답해주세요:
        [
          {
            "name": "식당명",
            "address": "%%s 근처 상세주소",
            "rating": 4.5,
            "largeCategory": "대카테고리 (예: %s)",
            "mediumCategory": "중카테고리 (예: 파스타, 스테이크, 초밥, 라멘, 해산물 등 구체적인 음식 종류)",
            "description": "식당의 특징, 분위기, 대표메뉴 등을 포함한 상세 설명 (2-3문장)",
            "representativeReview": "실제 방문객이 남길 법한 대표 리뷰 (1-2문장)"
          }
        ]

        각 식당에 대해:
        1. 실제로 존재하는 유명한 식당을 추천해주세요
        2. rating은 4.0~5.0 사이의 적절한 평점을 부여하세요 (소수점 첫째자리까지)
        3. largeCategory는 "%%s"를 기반으로 설정하세요
        4. mediumCategory는 해당 식당의 구체적인 음식 종류를 지정하세요
        5. description은 식당의 특징과 인기메뉴를 구체적으로 설명해주세요
        6. representativeReview는 긍정적이고 구체적인 경험을 담은 리뷰를 작성해주세요

        설명 없이 JSON 배열만 반환하세요.
        """,
        LARGE_CATEGORY_EXAMPLES
    );

    private static final String BATCH_RESTAURANT_GENERATION_TEMPLATE = String.format(
        """
        다음 지역들에서 각 카테고리별로 인기 있는 식당을 추천해주세요.

        지역: %%s
        카테고리: %%s
        각 지역-카테고리 조합당 %%d개씩 추천

        다음 JSON 형식으로만 응답해주세요:
        {
          "지역명": {
            "카테고리명": [
              {
                "name": "식당명",
                "address": "해당 지역 근처 상세주소",
                "rating": 4.5,
                "largeCategory": "대카테고리 (예: %s)",
                "mediumCategory": "중카테고리 (예: 파스타, 스테이크, 초밥, 라멘, 해산물 등 구체적인 음식 종류)",
                "description": "식당의 특징, 분위기, 대표메뉴 등을 포함한 상세 설명 (2-3문장)",
                "representativeReview": "실제 방문객이 남길 법한 대표 리뷰 (1-2문장)"
              }
            ]
          }
        }

        각 식당에 대해:
        1. 실제로 존재하는 유명한 식당을 추천해주세요
        2. rating은 4.0~5.0 사이의 적절한 평점을 부여하세요 (소수점 첫째자리까지)
        3. largeCategory는 요청한 카테고리를 기반으로 설정하세요
        4. mediumCategory는 해당 식당의 구체적인 음식 종류를 지정하세요
        5. description은 식당의 특징과 인기메뉴를 구체적으로 설명해주세요
        6. representativeReview는 긍정적이고 구체적인 경험을 담은 리뷰를 작성해주세요

        설명 없이 JSON 객체만 반환하세요.
        """,
        LARGE_CATEGORY_EXAMPLES
    );

    /**
     * 맛집 추천을 생성하기 위한 프롬프트 빌드
     *
     * @param location 장소명 (예: "홍대입구역", "강남역")
     * @param category 음식 카테고리 (예: "한식", "일식")
     * @param count 생성할 맛집 개수
     * @return 포맷팅된 프롬프트 문자열
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

    /**
     * 여러 장소-카테고리 조합에 대한 맛집을 생성하기 위한 배치 프롬프트 빌드
     * 모든 조합을 단일 프롬프트로 요청하여 API 호출 횟수 감소
     *
     * @param locations 장소명 리스트 (예: ["홍대입구역", "강남역"])
     * @param categories 음식 카테고리 리스트 (예: ["한식", "중식", "일식"])
     * @param countPerCombo 장소-카테고리 조합당 맛집 개수
     * @return 포맷팅된 배치 프롬프트 문자열
     */
    public String buildBatchRestaurantGenerationPrompt(
        List<String> locations,
        List<String> categories,
        int countPerCombo
    ) {
        String locationsStr = String.join(", ", locations);
        String categoriesStr = String.join(", ", categories);

        return String.format(
            BATCH_RESTAURANT_GENERATION_TEMPLATE,
            locationsStr,
            categoriesStr,
            countPerCombo
        );
    }
}
