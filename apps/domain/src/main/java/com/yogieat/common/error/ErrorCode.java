package com.yogieat.common.error;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    // Sample
    SAMPLE_ERROR("SAMPLE", "Sample Error Message"),

    // Common
    METHOD_ARGUMENT_TYPE_MISMATCH("C001", "요청 한 값 타입이 잘못되어 binding에 실패하였습니다."),
    METHOD_NOT_ALLOWED("C002", "지원하지 않는 HTTP method 입니다."),
    INTERNAL_SERVER_ERROR("C003", "서버 오류, 관리자에게 문의하세요"),

    // User
    USER_NOT_FOUND("U001", "해당 회원을 찾을 수 없습니다."),

    // Gemini API
    GEMINI_API_ERROR("G001", "Gemini API 호출 실패"),
    GEMINI_RESPONSE_PARSE_ERROR("G002", "Gemini 응답 파싱 실패"),

    // Kakao API
    KAKAO_API_ERROR("K001", "Kakao API 호출 실패"),
    KAKAO_RATE_LIMIT_EXCEEDED("K002", "Kakao API 요청 한도 초과"),

    // Restaurant Collection
    RESTAURANT_COLLECTION_FAILED("R001", "식당 수집 작업 실패"),
    INVALID_LOCATION_NAME("R002", "알 수 없는 지역명입니다"),
    INVALID_CATEGORY_NAME("R003", "알 수 없는 카테고리명입니다"),
    CATEGORY_NOT_FOUND("R005", "해당 카테고리를 찾을 수 없습니다"),
    RESTAURANT_NOT_FOUND("R004", "해당 맛집을 찾을 수 없습니다"),

    // Gathering
    GATHERING_NOT_FOUND("GA001", "해당 모임을 찾을 수 없습니다"),
    GATHERING_DELETED("GA002", "이미 삭제된 모임입니다"),
    GATHERING_FULL("GA003", "모임 인원이 가득 찼습니다"),
    GATHERING_PEOPLE_COUNT_REQUIRED("GA004", "모임 인원 수는 필수입니다"),
    GATHERING_PEOPLE_COUNT_OUT_OF_RANGE("GA005", "모임 인원 수는 1 이상 10 이하여야 합니다"),
    GATHERING_SCHEDULED_DATE_REQUIRED("GA006", "모임 날짜는 필수입니다"),
    GATHERING_SCHEDULED_DATE_PAST("GA007", "과거 날짜로는 모임을 생성할 수 없습니다"),

    // Participant
    PARTICIPANT_DISLIKES_EXCEEDED("P001", "비선호 음식은 최소 1개, 최대 4개까지 입력 가능합니다"),
    PARTICIPANT_PREFERENCES_EXCEEDED("P002", "선호 음식은 최대 3개까지 입력 가능합니다"),
    PARTICIPANT_NICKNAME_REQUIRED("P003", "닉네임은 필수입니다"),
    PARTICIPANT_NICKNAME_TOO_LONG("P004", "닉네임은 최대 8자까지 입력 가능합니다"),
    PARTICIPANT_NICKNAME_INVALID("P005", "닉네임에 숫자 또는 특수문자는 사용할 수 없습니다"),
    DUPLICATE_NICKNAME("P006", "이미 입장한 사용자입니다"),

    // Lock
    LOCK_TIMEOUT("L001", "락 획득 시간이 초과되었습니다"),

    // Recommend
    INVALID_CATEGORY_AGGREGATION("REC001", "카테고리 집계 데이터가 올바르지 않습니다"),
    INVALID_PREFERENCE_SCORE("REC002", "선호도 점수 상태가 올바르지 않습니다"),
    ;

    private final String code;
    private final String message;
}
