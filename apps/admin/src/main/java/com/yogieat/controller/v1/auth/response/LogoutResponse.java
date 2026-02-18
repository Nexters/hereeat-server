package com.yogieat.controller.v1.auth.response;

import com.yogieat.service.auth.result.LogoutResult;

public record LogoutResponse(
        boolean success,
        String message
) {
    public static LogoutResponse from(LogoutResult result) {
        return new LogoutResponse(result.success(), result.message());
    }
}
