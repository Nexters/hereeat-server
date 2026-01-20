package com.yogieat.domain.category.service;

import com.yogieat.domain.category.domain.Category;
import java.util.Optional;

public interface CategoryRepository {
    Optional<Category> findByLargeCategoryAndMediumCategory(String largeCategory, String mediumCategory);
    Category save(Category category);
}
