package com.yogieat.datasource.db.core.category;

import com.yogieat.category.domain.Category;
import com.yogieat.category.domain.value.LargeCategory;
import com.yogieat.datasource.db.core.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "t_category",
    indexes = {
        @Index(
            name = "idx_category_large_medium",
            columnList = "large_category, medium_category",
            unique = true
        )
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CategoryEntity extends BaseEntity {
    @Column(columnDefinition = "VARCHAR(15)")
    @Enumerated(EnumType.STRING)
    private LargeCategory largeCategory;
    private String mediumCategory;

    @Builder(access = AccessLevel.PRIVATE)
    private CategoryEntity(LargeCategory largeCategory, String mediumCategory) {
        this.largeCategory = largeCategory;
        this.mediumCategory = mediumCategory;
    }

    public static CategoryEntity of(LargeCategory largeCategory, String mediumCategory) {
        return CategoryEntity.builder()
                .largeCategory(largeCategory)
                .mediumCategory(mediumCategory)
                .build();
    }

    public static Category toDomain(CategoryEntity entity) {
        return new Category(
                entity.getId(),
                entity.getLargeCategory(),
                entity.getMediumCategory(),
                entity.getCreatedAt()
        );
    }
}
