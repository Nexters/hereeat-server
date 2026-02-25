package com.yogieat.category.service;

import com.yogieat.category.domain.Category;
import com.yogieat.category.domain.value.LargeCategory;
import com.yogieat.util.LockManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryService {
    private static final String CATEGORY_LOCK_PREFIX = "category:";

    private final CategoryRepository categoryRepository;
    private final ObjectProvider<CacheManager> cacheManagerProvider;
    private final LockManager lockManager;

    @Transactional
    public Long findOrCreateCategory(LargeCategory largeCategory, String mediumCategory) {
        String lockKey = buildCategoryLockKey(largeCategory, mediumCategory);
        return lockManager.executeWithLock(lockKey, () -> findOrCreateWithLock(largeCategory, mediumCategory));
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

    private Long findOrCreateWithLock(LargeCategory largeCategory, String mediumCategory) {
        return categoryRepository.findByLargeCategoryAndMediumCategory(largeCategory, mediumCategory)
                .map(Category::id)
                .orElseGet(() -> createCategoryHandlingRace(largeCategory, mediumCategory));
    }

    private Long createCategoryHandlingRace(LargeCategory largeCategory, String mediumCategory) {
        try {
            Category category = new Category(null, largeCategory, mediumCategory, null);
            Long savedCategoryId = categoryRepository.save(category).id();
            evictCategoriesCache();
            return savedCategoryId;
        } catch (DataIntegrityViolationException e) {
            // 유니크 제약이 존재하는 환경에서 동시 생성 충돌 시 재조회
            return categoryRepository.findByLargeCategoryAndMediumCategory(largeCategory, mediumCategory)
                    .map(Category::id)
                    .orElseThrow(() -> new IllegalStateException(
                            "Category should exist after DataIntegrityViolationException", e));
        }
    }

    private String buildCategoryLockKey(LargeCategory largeCategory, String mediumCategory) {
        return CATEGORY_LOCK_PREFIX + largeCategory + ":" + mediumCategory;
    }
}
