package com.yogieat.domain.category.service;

import com.yogieat.domain.category.domain.Category;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CategoryService {
    private final CategoryRepository categoryRepository;

    public Long findOrCreateCategory(String largeCategory, String mediumCategory) {
        return categoryRepository.findByLargeCategoryAndMediumCategory(largeCategory, mediumCategory)
            .map(Category::id)
            .orElseGet(() -> {
                Category category = new Category(null, largeCategory, mediumCategory);
                return categoryRepository.save(category).id();
            });
    }
}
