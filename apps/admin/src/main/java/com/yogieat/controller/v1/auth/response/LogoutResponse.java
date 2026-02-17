package com.yogieat.controller.v1.auth.response;

import com.yogieat.service.AuthFacade;

public record LogoutResponse(
        boolean success,
        String message
) {
    public static LogoutResponse from(AuthFacade.LogoutResult result) {
        return new LogoutResponse(result.success(), result.message());
    }
}
