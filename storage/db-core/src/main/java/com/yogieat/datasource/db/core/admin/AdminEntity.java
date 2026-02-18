package com.yogieat.datasource.db.core.admin;

import com.yogieat.admin.domain.Admin;
import com.yogieat.admin.domain.value.AdminRole;
import com.yogieat.datasource.db.core.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "t_admin")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminEntity extends BaseEntity {

    @Column(name = "login_id", nullable = false, unique = true)
    private String loginId;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private AdminRole role;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Builder(access = AccessLevel.PRIVATE)
    public AdminEntity(
            String loginId, String password, String name, AdminRole role, LocalDateTime lastLoginAt) {
        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.role = role;
        this.lastLoginAt = lastLoginAt;
    }

    public static AdminEntity from(Admin admin) {
        return AdminEntity.builder()
                .loginId(admin.loginId())
                .password(admin.password())
                .name(admin.name())
                .role(admin.role())
                .lastLoginAt(admin.lastLoginAt())
                .build();
    }

    public static Admin toDomain(AdminEntity entity) {
        return new Admin(
                entity.getId(),
                entity.getLoginId(),
                entity.getPassword(),
                entity.getName(),
                entity.getRole(),
                entity.getLastLoginAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getDeletedAt());
    }

    public void updateLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }
}
