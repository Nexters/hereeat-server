package com.yogieat.config.jwt;

import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret,
        long accessTokenValidity,
        long refreshTokenValidity
) {
    public JwtProperties {
        if (secret == null || secret.length() < 32) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        if (accessTokenValidity <= 0 || refreshTokenValidity <= 0) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
