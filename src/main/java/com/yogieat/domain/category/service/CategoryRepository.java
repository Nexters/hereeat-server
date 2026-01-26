package com.yogieat.domain.category.service;

import com.yogieat.domain.category.domain.Category;
import com.yogieat.domain.category.domain.value.LargeCategory;
import java.util.List;
import java.util.Optional;

public interface CategoryRepository {
    Optional<Category> findByLargeCategoryAndMediumCategory(LargeCategory largeCategory, String mediumCategory);
    Category save(Category category);
    List<Category> findAll();
}
