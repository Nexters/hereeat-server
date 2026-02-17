package com.yogieat.controller.advice;

public record ErrorResponse(String errorCode, String message) {

    public static ErrorResponse of(String errorCode, String message) {
        return new ErrorResponse(errorCode, message);
    }
}
