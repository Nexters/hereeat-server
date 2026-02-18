package com.yogieat.datasource.db.core.admin;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminJpaRepository extends JpaRepository<AdminEntity, Long> {

    Optional<AdminEntity> findByLoginIdAndDeletedAtIsNull(String loginId);

    Optional<AdminEntity> findByIdAndDeletedAtIsNull(Long id);
}
