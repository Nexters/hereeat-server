package com.yogieat.controller.v1.auth.request;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank
        String loginId,

        @NotBlank
        String password
) {
}
