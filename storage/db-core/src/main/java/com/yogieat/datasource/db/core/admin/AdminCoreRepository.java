package com.yogieat.datasource.db.core.admin;

import com.yogieat.admin.domain.Admin;
import com.yogieat.admin.service.AdminRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class AdminCoreRepository implements AdminRepository {

    private final AdminJpaRepository adminJpaRepository;

    @Override
    public Optional<Admin> findByEmail(String email) {
        return adminJpaRepository.findByEmailAndDeletedAtIsNull(email).map(AdminEntity::toDomain);
    }

    @Override
    public Optional<Admin> findById(Long id) {
        return adminJpaRepository.findByIdAndDeletedAtIsNull(id).map(AdminEntity::toDomain);
    }

    @Override
    public Admin save(Admin admin) {
        AdminEntity entity = AdminEntity.from(admin);
        AdminEntity savedEntity = adminJpaRepository.save(entity);
        return AdminEntity.toDomain(savedEntity);
    }

    @Override
    @Transactional
    public void updateLastLoginAt(Long adminId) {
        adminJpaRepository
                .findByIdAndDeletedAtIsNull(adminId)
                .ifPresent(entity -> entity.updateLastLoginAt(LocalDateTime.now()));
    }
}
