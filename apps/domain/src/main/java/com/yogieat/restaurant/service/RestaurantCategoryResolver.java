package com.yogieat.restaurant.service;

import com.yogieat.category.domain.value.LargeCategory;
import java.util.Locale;

public final class RestaurantCategoryResolver {

    private RestaurantCategoryResolver() {
    }

    public static CategoryResolution resolveForCollection(
            String requestedLargeCategoryDisplayName,
            String suggestionLargeCategoryDisplayName,
            String suggestionMediumCategory,
            LargeCategory apiLargeCategory,
            String apiMediumCategory,
            String apiCategoryName2,
            String apiCategoryName3
    ) {
        CategoryResolution fromKakao = resolveFromKakao(
                apiLargeCategory,
                apiMediumCategory,
                apiCategoryName2,
                apiCategoryName3
        );
        if (fromKakao != null) {
            return fromKakao;
        }

        LargeCategory requested = LargeCategory.fromDisplayName(requestedLargeCategoryDisplayName);
        if (requested != null) {
            return new CategoryResolution(requested, normalizeMediumCategory(suggestionMediumCategory));
        }

        LargeCategory suggestion = LargeCategory.fromDisplayName(suggestionLargeCategoryDisplayName);
        if (suggestion != null) {
            return new CategoryResolution(suggestion, normalizeMediumCategory(suggestionMediumCategory));
        }

        return null;
    }

    public static CategoryResolution resolveFromKakao(
            LargeCategory apiLargeCategory,
            String apiMediumCategory,
            String apiCategoryName2,
            String apiCategoryName3
    ) {
        if (apiLargeCategory != null) {
            String medium = firstNonBlank(apiMediumCategory, apiCategoryName3);
            return new CategoryResolution(apiLargeCategory, normalizeMediumCategory(medium));
        }

        LargeCategory inferred = inferLargeCategoryFromKeywords(apiCategoryName2, apiCategoryName3);
        if (inferred == null) {
            return null;
        }

        return new CategoryResolution(inferred, normalizeMediumCategory(apiCategoryName3));
    }

    private static LargeCategory inferLargeCategoryFromKeywords(String... sources) {
        String text = normalizeText(sources);
        if (text.isBlank()) {
            return null;
        }

        if (containsAny(text, "중식", "중국", "중화", "짜장", "짬뽕", "마라", "훠궈", "탕수육", "딤섬", "광동", "사천", "양꼬치")) {
            return LargeCategory.CHINESE;
        }
        if (containsAny(text, "일식", "일본", "초밥", "스시", "라멘", "우동", "소바", "돈까스", "돈카츠", "오마카세", "텐동", "사시미", "이자카야")) {
            return LargeCategory.JAPANESE;
        }
        if (containsAny(text, "아시안", "동남아", "태국", "베트남", "인도", "인도네시아", "말레이", "커리", "쌀국수", "팟타이", "분짜", "월남")) {
            return LargeCategory.ASIAN;
        }
        if (containsAny(text, "양식", "파스타", "스테이크", "피자", "브런치", "햄버거", "이탈리안", "프렌치", "멕시칸", "타코", "리조또", "그릴", "와인바", "와인 펍")) {
            return LargeCategory.WESTERN;
        }
        if (containsAny(text, "한식", "국밥", "백반", "찌개", "한정식", "냉면", "설렁탕", "곰탕", "칼국수", "분식", "김밥", "삼겹", "족발", "보쌈", "감자탕", "해장국", "닭갈비", "찜닭", "순대", "곱창", "불고기")) {
            return LargeCategory.KOREAN;
        }

        return null;
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeText(String... sources) {
        StringBuilder builder = new StringBuilder();
        for (String source : sources) {
            if (source == null || source.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(source.strip().toLowerCase(Locale.ROOT));
        }
        return builder.toString();
    }

    private static String normalizeMediumCategory(String mediumCategory) {
        if (mediumCategory == null || mediumCategory.isBlank()) {
            return null;
        }
        return mediumCategory.strip();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.strip();
            }
        }
        return null;
    }

    public record CategoryResolution(
            LargeCategory largeCategory,
            String mediumCategory
    ) {
    }
}
