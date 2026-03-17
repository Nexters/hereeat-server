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
    SYNC_JOB_NOT_FOUND("R006", "해당 동기화 Job을 찾을 수 없습니다"),
    SYNC_JOB_CONFLICT("R007", "이미 실행 중인 동기화 Job이 존재합니다"),
    RESTAURANT_SYNC_FAILED("R008", "맛집 동기화 작업이 실패했습니다"),

    // Gathering
    GATHERING_NOT_FOUND("GA001", "해당 모임을 찾을 수 없습니다"),
    GATHERING_DELETED("GA002", "이미 삭제된 모임입니다"),
    GATHERING_FULL("GA003", "모임 인원이 가득 찼습니다"),
    GATHERING_PEOPLE_COUNT_REQUIRED("GA004", "모임 인원 수는 필수입니다"),
    GATHERING_PEOPLE_COUNT_OUT_OF_RANGE("GA005", "모임 인원 수는 1 이상 10 이하여야 합니다"),
    GATHERING_SCHEDULED_DATE_REQUIRED("GA006", "모임 날짜는 필수입니다"),
    GATHERING_SCHEDULED_DATE_PAST("GA007", "과거 날짜로는 모임을 생성할 수 없습니다"),

    // Participant
    PARTICIPANT_DISLIKES_EXCEEDED("P001", "비선호 음식은 최소 1개, 최대 2개까지 입력 가능합니다"),
    PARTICIPANT_PREFERENCES_EXCEEDED("P002", "선호 음식은 최대 3개까지 입력 가능합니다"),
    PARTICIPANT_NICKNAME_REQUIRED("P003", "닉네임은 필수입니다"),
    PARTICIPANT_NICKNAME_TOO_LONG("P004", "닉네임은 최대 8자까지 입력 가능합니다"),
    PARTICIPANT_NICKNAME_INVALID("P005", "닉네임에 숫자 또는 특수문자는 사용할 수 없습니다"),
    DUPLICATE_NICKNAME("P006", "이미 입장한 사용자입니다"),
    PARTICIPANT_MAJORITY_NOT_REACHED("P007", "추천 진행을 위한 과반수 인원이 채워지지 않았습니다"),

    // Lock
    LOCK_TIMEOUT("L001", "락 획득 시간이 초과되었습니다"),

    // Recommend
    INVALID_CATEGORY_AGGREGATION("REC001", "카테고리 집계 데이터가 올바르지 않습니다"),
    INVALID_PREFERENCE_SCORE("REC002", "선호도 점수 상태가 올바르지 않습니다"),
    RECOMMEND_ALREADY_PROCEEDED("REC003", "이미 추천이 진행 중이거나 완료되었습니다"),
    RECOMMEND_RESULT_NOT_FOUND("REC004", "해당 모임의 추천 결과를 찾을 수 없습니다"),
    RECOMMEND_REROLL_NOT_AVAILABLE("REC005", "추천이 완료된 모임만 재추천할 수 있습니다"),
    RECOMMEND_REROLL_LIMIT_EXCEEDED("REC006", "재추천 가능 횟수를 초과했습니다"),

    // Admin
    ADMIN_NOT_FOUND("A001", "해당 관리자를 찾을 수 없습니다"),
    ADMIN_INVALID_PASSWORD("A002", "비밀번호가 일치하지 않습니다"),
    ADMIN_UNAUTHORIZED("A003", "인증이 필요합니다"),
    ADMIN_FORBIDDEN("A004", "접근 권한이 없습니다"),
    ADMIN_TOKEN_EXPIRED("A005", "토큰이 만료되었습니다"),
    ADMIN_TOKEN_INVALID("A006", "유효하지 않은 토큰입니다"),

    ;

    private final String code;
    private final String message;
}
