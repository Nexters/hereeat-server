package com.yogieat.domain.category.domain.value;

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
}
