package com.yogieat.global.error;

import org.springframework.http.HttpStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

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
	;

	private final HttpStatus status;
	private final String code;
	private final String message;
}
