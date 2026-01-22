package com.yogieat.external.ai.gemini;

import org.jspecify.annotations.NonNull;

/**
 * 장소와 카테고리별로 맛집 제안을 그룹화하기 위한 키
 * 배치 처리에서 API 응답을 구조화하는 데 사용
 */
public record LocationCategoryKey(
    String location,
    String category
) {
    @Override
    public @NonNull String toString() {
        return location + "-" + category;
    }
}
