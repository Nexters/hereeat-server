package com.yogieat.fixture;

import com.yogieat.admin.domain.Admin;
import com.yogieat.admin.domain.value.AdminRole;
import com.yogieat.config.jwt.JwtProperties;
import com.yogieat.config.jwt.JwtTokenProvider;

public final class AdminTestFixture {

    public static final String SECRET_KEY = "this-is-a-sufficiently-long-test-secret-key-12345";
    public static final long ACCESS_TOKEN_VALIDITY = 1_000L;
    public static final long REFRESH_TOKEN_VALIDITY = 2_000L;
    public static final long SHORT_ACCESS_TOKEN_VALIDITY = 10L;
    public static final String DEFAULT_LOGIN_ID = "admin";
    public static final String DEFAULT_PASSWORD = "encoded-password";
    public static final String DEFAULT_NAME = "Admin";

    private AdminTestFixture() {
    }

    public static Admin admin() {
        return admin(
                1L,
                DEFAULT_LOGIN_ID,
                DEFAULT_PASSWORD,
                DEFAULT_NAME,
                AdminRole.ADMIN
        );
    }

    public static Admin admin(long id, String loginId, String password, String name) {
        return admin(id, loginId, password, name, AdminRole.ADMIN);
    }

    public static Admin admin(long id, String loginId, String password, String name, AdminRole role) {
        return Admin.of(id, loginId, password, name, role);
    }

    public static Admin admin(Long id, String loginId, String password) {
        return admin(id, loginId, password, "Admin", AdminRole.ADMIN);
    }

    public static Admin admin(Long id, String loginId, String password, String name, AdminRole role) {
        return Admin.of(id, loginId, password, name, role);
    }

    public static JwtProperties jwtProperties(long accessTokenValidity, long refreshTokenValidity) {
        return JwtProperties.of(SECRET_KEY, accessTokenValidity, refreshTokenValidity);
    }

    public static JwtTokenProvider jwtTokenProvider(long accessTokenValidity, long refreshTokenValidity) {
        return JwtTokenProvider.of(SECRET_KEY, accessTokenValidity, refreshTokenValidity);
    }

    public static JwtTokenProvider defaultJwtTokenProvider() {
        return jwtTokenProvider(ACCESS_TOKEN_VALIDITY, REFRESH_TOKEN_VALIDITY);
    }

    public static JwtTokenProvider shortAccessJwtTokenProvider() {
        return jwtTokenProvider(SHORT_ACCESS_TOKEN_VALIDITY, REFRESH_TOKEN_VALIDITY);
    }
}
