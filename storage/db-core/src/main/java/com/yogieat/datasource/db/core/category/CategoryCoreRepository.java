package com.yogieat.datasource.db.core.category;

import com.yogieat.category.domain.Category;
import com.yogieat.category.domain.value.LargeCategory;
import com.yogieat.category.service.CategoryRepository;
import java.util.List;
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

    @Override
    public List<Category> findAll() {
        List<CategoryEntity> entities = categoryJpaRepository.findAll();
        return entities.stream()
                .map(CategoryEntity::toDomain)
                .toList();
    }
}
