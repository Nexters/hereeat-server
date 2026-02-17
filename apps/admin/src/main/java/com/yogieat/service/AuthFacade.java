package com.yogieat.service;

import com.yogieat.admin.domain.Admin;
import com.yogieat.admin.service.AdminService;
import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import com.yogieat.config.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthFacade {

    private final AdminService adminService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public LoginResult login(String email, String password) {
        Admin admin = adminService.getByEmail(email);

        if (!passwordEncoder.matches(password, admin.password())) {
            throw new CustomException(ErrorCode.ADMIN_INVALID_PASSWORD);
        }

        adminService.updateLastLoginAt(admin.id());

        String accessToken = jwtTokenProvider.createAccessToken(admin);
        String refreshToken = jwtTokenProvider.createRefreshToken(admin);

        return new LoginResult(
                accessToken,
                refreshToken,
                "Bearer",
                jwtTokenProvider.getAccessTokenValidity(),
                jwtTokenProvider.getRefreshTokenValidity()
        );
    }

    public LogoutResult logout() {
        return new LogoutResult(true, "로그아웃 되었습니다");
    }

    public record LoginResult(
            String accessToken,
            String refreshToken,
            String tokenType,
            long accessTokenExpiresIn,
            long refreshTokenExpiresIn
    ) {
    }

    public record LogoutResult(boolean success, String message) {
    }
}
