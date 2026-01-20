package com.yogieat.domain.category.repository;

import com.yogieat.domain.category.entity.CategoryEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryJpaRepository extends JpaRepository<CategoryEntity, Long> {
    Optional<CategoryEntity> findByLargeCategoryAndMediumCategory(String largeCategory, String mediumCategory);
}
