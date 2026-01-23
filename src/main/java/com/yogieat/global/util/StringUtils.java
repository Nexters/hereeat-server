package com.yogieat.global.util;

import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 문자열 관련 유틸리티 클래스
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StringUtils {

    private static final String DELIMITER = ",";

    /**
     * List<String>을 콤마로 구분된 String으로 변환
     *
     * @param list 변환할 문자열 리스트
     * @return 콤마로 구분된 문자열, 리스트가 null이거나 비어있으면 null 반환
     */
    public static String joinWithComma(List<String> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        return String.join(DELIMITER, list);
    }

    /**
     * 콤마로 구분된 String을 List<String>으로 변환
     *
     * @param str 변환할 문자열
     * @return 문자열 리스트, 입력이 null이거나 비어있으면 빈 리스트 반환
     */
    public static List<String> splitByComma(String str) {
        if (str == null || str.isBlank()) {
            return List.of();
        }
        return List.of(str.split(DELIMITER));
    }

    /**
     * 문자열이 null이거나 비어있는지 확인
     *
     * @param str 확인할 문자열
     * @return null이거나 비어있으면 true
     */
    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }

    /**
     * 문자열이 null이거나 공백만 있는지 확인
     *
     * @param str 확인할 문자열
     * @return null이거나 공백만 있으면 true
     */
    public static boolean isBlank(String str) {
        return str == null || str.isBlank();
    }
}
