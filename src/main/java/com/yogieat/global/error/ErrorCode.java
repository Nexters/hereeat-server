package com.yogieat.global.error;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {
	// Sample
	SAMPLE_ERROR(HttpStatus.BAD_REQUEST, "SAMPLE", "Sample Error Message"),

	// Common
	METHOD_ARGUMENT_TYPE_MISMATCH(HttpStatus.BAD_REQUEST, "C001", "요청 한 값 타입이 잘못되어 binding에 실패하였습니다."),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "C002", "지원하지 않는 HTTP method 입니다."),
	INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C003", "서버 오류, 관리자에게 문의하세요"),

	// User
	USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U001", "해당 회원을 찾을 수 없습니다."),

	// Gemini API
	GEMINI_API_ERROR(HttpStatus.SERVICE_UNAVAILABLE, "G001", "Gemini API 호출 실패"),
	GEMINI_RESPONSE_PARSE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "G002", "Gemini 응답 파싱 실패"),

	// Kakao API
	KAKAO_API_ERROR(HttpStatus.SERVICE_UNAVAILABLE, "K001", "Kakao API 호출 실패"),
	KAKAO_RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "K002", "Kakao API 요청 한도 초과"),

	// Restaurant Collection
	RESTAURANT_COLLECTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "R001", "식당 수집 작업 실패"),
	;

	private final HttpStatus status;
	private final String code;
	private final String message;
}
