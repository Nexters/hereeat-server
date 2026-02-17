package com.yogieat.admin.service;

import com.yogieat.admin.domain.Admin;
import java.util.Optional;

public interface AdminRepository {

    Optional<Admin> findByEmail(String email);

    Optional<Admin> findById(Long id);

    Admin save(Admin admin);

    void updateLastLoginAt(Long adminId);
}
