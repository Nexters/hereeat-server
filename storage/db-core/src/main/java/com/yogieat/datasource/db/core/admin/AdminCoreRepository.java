package com.yogieat.datasource.db.core.admin;

import static com.yogieat.datasource.db.core.admin.QAdminEntity.adminEntity;

import com.querydsl.jpa.impl.JPAQueryFactory;
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
    private final JPAQueryFactory jpaQueryFactory;

    @Override
    public Optional<Admin> findByLoginId(String loginId) {
        return adminJpaRepository.findByLoginIdAndDeletedAtIsNull(loginId).map(AdminEntity::toDomain);
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
        jpaQueryFactory.update(adminEntity)
                .set(adminEntity.lastLoginAt, LocalDateTime.now())
                .where(
                        adminEntity.id.eq(adminId),
                        adminEntity.deletedAt.isNull()
                )
                .execute();
    }
}
