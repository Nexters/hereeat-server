package com.yogieat.admin.service;

import com.yogieat.admin.domain.Admin;
import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final AdminRepository adminRepository;

    @Transactional(readOnly = true)
    public Admin getByEmail(String email) {
        return adminRepository
                .findByEmail(email)
                .filter(Admin::isActive)
                .orElseThrow(() -> new CustomException(ErrorCode.ADMIN_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Admin getById(Long id) {
        return adminRepository
                .findById(id)
                .filter(Admin::isActive)
                .orElseThrow(() -> new CustomException(ErrorCode.ADMIN_NOT_FOUND));
    }

    @Transactional
    public void updateLastLoginAt(Long adminId) {
        adminRepository.updateLastLoginAt(adminId);
    }
}
