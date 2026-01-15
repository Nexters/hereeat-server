package com.yogieat.global.common.response;

import java.time.LocalDateTime;
import com.yogieat.global.error.ErrorResponse;

public record GlobalApiResponse(int status, Object data, LocalDateTime timestamp) {
    public static GlobalApiResponse success(int status, Object data) {
        return new GlobalApiResponse(status, data, LocalDateTime.now());
    }

    public static GlobalApiResponse fail(int status, ErrorResponse errorResponse) {
        return new GlobalApiResponse(status, errorResponse, LocalDateTime.now());
    }
}
