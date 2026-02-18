package com.yogieat.config.web;

import com.yogieat.admin.domain.Admin;
import com.yogieat.admin.domain.value.AdminRole;

public record AdminUser(
        Long id,
        String loginId,
        AdminRole role
) {
    public static AdminUser from(Admin admin) {
        return new AdminUser(admin.id(), admin.loginId(), admin.role());
    }
}
