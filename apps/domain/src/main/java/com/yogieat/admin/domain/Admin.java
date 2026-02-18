package com.yogieat.admin.domain;

import com.yogieat.admin.domain.value.AdminRole;
import java.time.LocalDateTime;

public record Admin(
        Long id,
        String loginId,
        String password, // BCrypt 해시된 비밀번호
        String name,
        AdminRole role,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deletedAt) {

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isActive() {
        return !isDeleted();
    }
}
