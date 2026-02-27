package com.yogieat.category.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yogieat.category.domain.Category;
import com.yogieat.category.domain.value.LargeCategory;
import com.yogieat.util.LockManager;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ObjectProvider<CacheManager> cacheManagerProvider;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    @Mock
    private LockManager lockManager;

    @InjectMocks
    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        when(lockManager.executeWithLock(anyString(), Mockito.<LockManager.Task<Long>>any()))
                .thenAnswer(invocation -> {
                    LockManager.Task<Long> task = invocation.<LockManager.Task<Long>>getArgument(1);
                    return task.execute();
                });
    }

    @Test
    @DisplayName("카테고리가 이미 존재하면 기존 ID를 반환한다")
    void shouldReturnExistingCategoryId() {
        // given
        LargeCategory largeCategory = LargeCategory.KOREAN;
        String mediumCategory = "국밥";
        Category existing = new Category(1L, largeCategory, mediumCategory, null);

        when(categoryRepository.findByLargeCategoryAndMediumCategory(largeCategory, mediumCategory))
                .thenReturn(Optional.of(existing));

        // when
        Long categoryId = categoryService.findOrCreateCategory(largeCategory, mediumCategory);

        // then
        assertThat(categoryId).isEqualTo(1L);
        verify(categoryRepository, never()).save(any());
        verify(lockManager).executeWithLock(eq("category:KOREAN:국밥"), any());
    }

    @Test
    @DisplayName("카테고리가 없으면 생성 후 캐시를 비운다")
    void shouldCreateCategoryAndEvictCache() {
        // given
        LargeCategory largeCategory = LargeCategory.JAPANESE;
        String mediumCategory = "라멘";
        Category saved = new Category(10L, largeCategory, mediumCategory, null);

        when(categoryRepository.findByLargeCategoryAndMediumCategory(largeCategory, mediumCategory))
                .thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenReturn(saved);
        when(cacheManagerProvider.getIfAvailable()).thenReturn(cacheManager);
        when(cacheManager.getCache("categories")).thenReturn(cache);

        // when
        Long categoryId = categoryService.findOrCreateCategory(largeCategory, mediumCategory);

        // then
        assertThat(categoryId).isEqualTo(10L);
        verify(categoryRepository).save(any(Category.class));
        verify(cache).clear();
    }

    @Test
    @DisplayName("동시 생성 충돌(DataIntegrityViolation) 시 재조회한 ID를 반환한다")
    void shouldRecoverFromDataIntegrityViolationByRefetching() {
        // given
        LargeCategory largeCategory = LargeCategory.ASIAN;
        String mediumCategory = "쌀국수";
        Category createdByOtherTx = new Category(22L, largeCategory, mediumCategory, null);

        when(categoryRepository.findByLargeCategoryAndMediumCategory(largeCategory, mediumCategory))
                .thenReturn(Optional.empty(), Optional.of(createdByOtherTx));
        when(categoryRepository.save(any(Category.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        // when
        Long categoryId = categoryService.findOrCreateCategory(largeCategory, mediumCategory);

        // then
        assertThat(categoryId).isEqualTo(22L);
        verify(categoryRepository).save(any(Category.class));
        verify(cacheManagerProvider, never()).getIfAvailable();
    }
}
