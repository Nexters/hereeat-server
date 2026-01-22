package com.yogieat.domain.category.repository;

import com.yogieat.domain.category.domain.Category;
import com.yogieat.domain.category.domain.value.LargeCategory;
import com.yogieat.domain.category.entity.CategoryEntity;
import com.yogieat.domain.category.service.CategoryRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CategoryCoreRepository implements CategoryRepository {
    private final CategoryJpaRepository categoryJpaRepository;

    @Override
    public Optional<Category> findByLargeCategoryAndMediumCategory(LargeCategory largeCategory, String mediumCategory) {
        return categoryJpaRepository.findByLargeCategoryAndMediumCategory(largeCategory, mediumCategory)
                .map(CategoryEntity::toDomain);
    }

    @Override
    public Category save(Category category) {
        CategoryEntity entity = CategoryEntity.of(category.largeCategory(), category.mediumCategory());
        CategoryEntity savedEntity = categoryJpaRepository.save(entity);
        return CategoryEntity.toDomain(savedEntity);
    }
}
