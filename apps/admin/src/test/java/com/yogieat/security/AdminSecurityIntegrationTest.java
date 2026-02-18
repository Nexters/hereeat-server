package com.yogieat.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yogieat.admin.domain.Admin;
import com.yogieat.admin.domain.value.AdminRole;
import com.yogieat.common.error.ErrorCode;
import com.yogieat.config.jwt.JwtTokenProvider;
import com.yogieat.datasource.db.core.admin.AdminEntity;
import com.yogieat.datasource.db.core.admin.AdminJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminJpaRepository adminJpaRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        adminJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("토큰 없이 보호 엔드포인트 호출 시 401을 반환한다")
    void accessWithoutTokenShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/test"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data.errorCode").value(ErrorCode.ADMIN_UNAUTHORIZED.getCode()));
    }

    @Test
    @DisplayName("유효한 ADMIN 토큰으로 /test 호출 시 200을 반환한다")
    void accessWithAdminTokenShouldReturn200() throws Exception {
        Admin admin = saveAdmin();
        String accessToken = jwtTokenProvider.createAccessToken(admin);

        mockMvc.perform(
                        get("/api/v1/admin/test")
                                .header("Authorization", "Bearer " + accessToken)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("admin authenticated"));
    }

    @Test
    @DisplayName("토큰은 유효하지만 관리자가 존재하지 않으면 401을 반환한다")
    void accessWithMissingAdminShouldReturn401() throws Exception {
        Admin missingAdmin = new Admin(
                999_999L,
                "missing-admin",
                "encoded-password",
                "Missing",
                AdminRole.ADMIN,
                null,
                null,
                null,
                null
        );
        String accessToken = jwtTokenProvider.createAccessToken(missingAdmin);

        mockMvc.perform(
                        get("/api/v1/admin/test")
                                .header("Authorization", "Bearer " + accessToken)
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data.errorCode").value(ErrorCode.ADMIN_NOT_FOUND.getCode()));
    }

    private Admin saveAdmin() {
        Admin source = new Admin(
                null,
                "admin",
                passwordEncoder.encode("admin123!"),
                "Admin",
                AdminRole.ADMIN,
                null,
                null,
                null,
                null
        );

        AdminEntity savedEntity = adminJpaRepository.save(AdminEntity.from(source));
        return AdminEntity.toDomain(savedEntity);
    }
}
