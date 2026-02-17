package com.yogieat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yogieat.admin.domain.Admin;
import com.yogieat.admin.domain.value.AdminRole;
import com.yogieat.admin.service.AdminService;
import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import com.yogieat.config.jwt.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthFacadeTest {

    @Mock
    private AdminService adminService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthFacade authFacade;

    @Test
    @DisplayName("로그인 성공 시 토큰을 반환하고 마지막 로그인 시간을 갱신한다")
    void loginSuccess() {
        String rawPassword = "admin123!";
        Admin admin = new Admin(
                1L,
                "admin@yogieat.com",
                "encoded-password",
                "Admin",
                AdminRole.ADMIN,
                null,
                null,
                null,
                null
        );

        when(adminService.getByEmail(admin.email())).thenReturn(admin);
        when(passwordEncoder.matches(rawPassword, admin.password())).thenReturn(true);
        when(jwtTokenProvider.createAccessToken(admin)).thenReturn("access-token");
        when(jwtTokenProvider.createRefreshToken(admin)).thenReturn("refresh-token");
        when(jwtTokenProvider.getAccessTokenValidity()).thenReturn(3_600_000L);
        when(jwtTokenProvider.getRefreshTokenValidity()).thenReturn(604_800_000L);

        AuthFacade.LoginResult result = authFacade.login(admin.email(), rawPassword);

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.accessTokenExpiresIn()).isEqualTo(3_600_000L);
        assertThat(result.refreshTokenExpiresIn()).isEqualTo(604_800_000L);
        verify(adminService).updateLastLoginAt(admin.id());
    }

    @Test
    @DisplayName("비밀번호 불일치 시 예외가 발생하고 로그인 시간은 갱신하지 않는다")
    void invalidPassword() {
        String rawPassword = "wrong-password";
        Admin admin = new Admin(
                1L,
                "admin@yogieat.com",
                "encoded-password",
                "Admin",
                AdminRole.ADMIN,
                null,
                null,
                null,
                null
        );

        when(adminService.getByEmail(admin.email())).thenReturn(admin);
        when(passwordEncoder.matches(rawPassword, admin.password())).thenReturn(false);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> authFacade.login(admin.email(), rawPassword)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ADMIN_INVALID_PASSWORD);
        verify(adminService, never()).updateLastLoginAt(admin.id());
    }

    @Test
    @DisplayName("존재하지 않는 계정 로그인 시 도메인 예외를 그대로 전파한다")
    void adminNotFound() {
        String email = "missing@yogieat.com";
        String password = "admin123!";
        CustomException notFound = new CustomException(ErrorCode.ADMIN_NOT_FOUND);

        when(adminService.getByEmail(email)).thenThrow(notFound);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> authFacade.login(email, password)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ADMIN_NOT_FOUND);
        verify(adminService, never()).updateLastLoginAt(1L);
    }
}
