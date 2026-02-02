package com.yogieat.datasource.db.core.category;

import com.yogieat.category.domain.value.LargeCategory;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryJpaRepository extends JpaRepository<CategoryEntity, Long> {
    Optional<CategoryEntity> findByLargeCategoryAndMediumCategory(LargeCategory largeCategory, String mediumCategory);
}
