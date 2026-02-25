package com.yogieat.category.service;

import com.yogieat.category.domain.Category;
import com.yogieat.category.domain.value.LargeCategory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final ObjectProvider<CacheManager> cacheManagerProvider;

    @Transactional
    public Long findOrCreateCategory(LargeCategory largeCategory, String mediumCategory) {
        return categoryRepository.findByLargeCategoryAndMediumCategory(largeCategory, mediumCategory)
            .map(Category::id)
            .orElseGet(() -> {
                Category category = new Category(null, largeCategory, mediumCategory, null);
                Long savedCategoryId = categoryRepository.save(category).id();
                evictCategoriesCache();
                return savedCategoryId;
            });
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "categories")
    public List<Category> findAll() {
        return categoryRepository.findAll();
    }

    private void evictCategoriesCache() {
        CacheManager cacheManager = cacheManagerProvider.getIfAvailable();
        if (cacheManager == null) {
            return;
        }

        Cache cache = cacheManager.getCache("categories");
        if (cache != null) {
            cache.clear();
        }
    }
}
