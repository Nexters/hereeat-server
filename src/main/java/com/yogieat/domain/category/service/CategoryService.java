package com.yogieat.domain.category.service;

import com.yogieat.domain.category.domain.Category;
import com.yogieat.domain.category.domain.value.LargeCategory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CategoryService {
    private final CategoryRepository categoryRepository;

    public Long findOrCreateCategory(LargeCategory largeCategory, String mediumCategory) {
        return categoryRepository.findByLargeCategoryAndMediumCategory(largeCategory, mediumCategory)
            .map(Category::id)
            .orElseGet(() -> {
                Category category = new Category(null, largeCategory, mediumCategory);
                return categoryRepository.save(category).id();
            });
    }

    @Cacheable("categories")
    public List<Category> findAll() {
        return categoryRepository.findAll();
    }
}
