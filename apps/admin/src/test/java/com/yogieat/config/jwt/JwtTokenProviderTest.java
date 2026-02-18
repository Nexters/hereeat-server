package com.yogieat.config.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.yogieat.admin.domain.Admin;
import com.yogieat.admin.domain.value.AdminRole;
import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private final JwtTokenProvider tokenProvider = new JwtTokenProvider(
            new JwtProperties(
                    "this-is-a-sufficiently-long-test-secret-key-12345",
                    1_000,
                    2_000
            )
    );

    private final Admin admin = new Admin(
            1L,
            "admin",
            "encoded-password",
            "Admin",
            AdminRole.ADMIN,
            null,
            null,
            null,
            null
    );

    @Test
    @DisplayName("Access 토큰 생성 후 파싱에 성공한다")
    void createAndParseAccessToken() {
        String token = tokenProvider.createAccessToken(admin);

        JwtTokenProvider.TokenPayload payload = tokenProvider.parseAccessToken(token);

        assertThat(payload.adminId()).isEqualTo(admin.id());
        assertThat(payload.loginId()).isEqualTo(admin.loginId());
        assertThat(payload.role()).isEqualTo(admin.role());
        assertThat(payload.tokenType()).isEqualTo("access");
    }

    @Test
    @DisplayName("Refresh 토큰을 Access 파서로 읽으면 유효하지 않은 토큰 예외가 발생한다")
    void parseRefreshTokenAsAccessTokenShouldFail() {
        String refreshToken = tokenProvider.createRefreshToken(admin);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> tokenProvider.parseAccessToken(refreshToken)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ADMIN_TOKEN_INVALID);
    }

    @Test
    @DisplayName("만료된 Access 토큰은 만료 예외가 발생한다")
    void expiredTokenShouldFail() throws InterruptedException {
        JwtTokenProvider shortLivedTokenProvider = new JwtTokenProvider(
                new JwtProperties(
                        "this-is-a-sufficiently-long-test-secret-key-12345",
                        10,
                        2_000
                )
        );

        String token = shortLivedTokenProvider.createAccessToken(admin);
        Thread.sleep(30);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> shortLivedTokenProvider.parseAccessToken(token)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ADMIN_TOKEN_EXPIRED);
    }
}
