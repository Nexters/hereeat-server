package com.yogieat.external.ai;

import com.yogieat.external.LocationCategoryKey;
import com.yogieat.restaurant.domain.SuggestionRestaurant;
import java.util.List;
import java.util.Map;

public interface GeminiClient {
    List<SuggestionRestaurant> generateRestaurants(String region, String category, int count);

    /**
     * 단일 API 호출로 여러 장소-카테고리 조합에 대한 맛집 생성
     * API 호출 횟수를 N번에서 1번으로 감소시켜 성능 및 비용 효율성 향상
     *
     * @param locations 장소명 리스트 (예: ["홍대입구역", "강남역"])
     * @param categories 음식 카테고리 리스트 (예: ["한식", "중식", "일식"])
     * @param countPerCombo 장소-카테고리 조합당 맛집 개수
     * @return LocationCategoryKey에 대한 맛집 제안 리스트의 맵
     */
    Map<LocationCategoryKey, List<SuggestionRestaurant>> generateRestaurantsBatch(
        List<String> locations,
        List<String> categories,
        String restaurantNames,
        int countPerCombo
    );
}
