package com.yogieat.service.auth;

import com.yogieat.admin.domain.Admin;
import com.yogieat.admin.service.AdminService;
import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import com.yogieat.config.jwt.JwtTokenProvider;
import com.yogieat.config.jwt.JwtTokenProvider.TokenPayload;
import com.yogieat.service.auth.result.LoginResult;
import com.yogieat.service.auth.result.LogoutResult;
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
    public LoginResult login(String loginId, String password) {
        Admin admin = adminService.getByLoginId(loginId);

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

    @Transactional
    public LoginResult refresh(String refreshToken) {
        TokenPayload payload = jwtTokenProvider.parseRefreshToken(refreshToken);
        Admin admin = adminService.getById(payload.adminId());

        if (!admin.loginId().equals(payload.loginId()) || admin.role() != payload.role()) {
            throw new CustomException(ErrorCode.ADMIN_TOKEN_INVALID);
        }

        String accessToken = jwtTokenProvider.createAccessToken(admin);
        String reissuedRefreshToken = jwtTokenProvider.createRefreshToken(admin);

        return new LoginResult(
                accessToken,
                reissuedRefreshToken,
                "Bearer",
                jwtTokenProvider.getAccessTokenValidity(),
                jwtTokenProvider.getRefreshTokenValidity()
        );
    }
}
