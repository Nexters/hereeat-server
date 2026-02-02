package com.yogieat.controller.advice;

import java.time.LocalDateTime;

public record GlobalApiResponse(int status, Object data, LocalDateTime timestamp) {
    public static GlobalApiResponse success(int status, Object data) {
        return new GlobalApiResponse(status, data, LocalDateTime.now());
    }

    public static GlobalApiResponse fail(int status, ErrorResponse errorResponse) {
        return new GlobalApiResponse(status, errorResponse, LocalDateTime.now());
    }
}
