package com.yogieat.category.domain.value;

import lombok.Getter;

@Getter
public enum LargeCategory {
    KOREAN("한식"),
    CHINESE("중식"),
    JAPANESE("일식"),
    WESTERN("양식"),
    ASIAN("아시안"),
    ANY("상관없음")
    ;
    private final String displayName;

    LargeCategory(String displayName) {
        this.displayName = displayName;
    }

    /**
     * displayName으로 LargeCategory enum을 찾습니다.
     *
     * @param displayName 한글 표시명 (예: "한식", "양식")
     * @return 해당하는 LargeCategory enum, 찾을 수 없으면 null
     */
    public static LargeCategory fromDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return null;
        }
        for (LargeCategory category : values()) {
            if (category.displayName.equals(displayName)) {
                return category;
            }
        }
        return null;
    }

    /**
     * displayName 또는 enum name으로 LargeCategory enum을 찾습니다.
     * displayName 우선 검색 후, 실패시 enum name으로 재시도합니다.
     *
     * @param value displayName (예: "한식", "양식") 또는 enum name (예: "KOREAN", "WESTERN")
     * @return 해당하는 LargeCategory enum, 찾을 수 없으면 null
     */
    public static LargeCategory fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        // 1. displayName으로 검색 시도
        LargeCategory category = fromDisplayName(value);
        if (category != null) {
            return category;
        }

        // 2. enum name으로 검색 시도
        try {
            return valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
