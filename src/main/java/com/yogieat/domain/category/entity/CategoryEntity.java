package com.yogieat.domain.category.entity;

import com.yogieat.domain.category.domain.Category;
import com.yogieat.global.common.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "t_category")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CategoryEntity extends BaseEntity {
    private String largeCategory;
    private String mediumCategory;

    @Builder(access = AccessLevel.PRIVATE)
    private CategoryEntity(String largeCategory, String mediumCategory) {
        this.largeCategory = largeCategory;
        this.mediumCategory = mediumCategory;
    }

    public static CategoryEntity of(String largeCategory, String mediumCategory) {
        return CategoryEntity.builder()
                .largeCategory(largeCategory)
                .mediumCategory(mediumCategory)
                .build();
    }

    public static Category toDomain(CategoryEntity entity) {
        return new Category(
                entity.getId(),
                entity.getLargeCategory(),
                entity.getMediumCategory()
        );
    }
}
