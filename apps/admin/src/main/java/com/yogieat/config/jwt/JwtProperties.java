package com.yogieat.config.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret,
        long accessTokenValidity,
        long refreshTokenValidity
) {
    public JwtProperties {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 characters");
        }
        if (accessTokenValidity <= 0 || refreshTokenValidity <= 0) {
            throw new IllegalArgumentException("JWT token validity must be greater than 0");
        }
    }
}
