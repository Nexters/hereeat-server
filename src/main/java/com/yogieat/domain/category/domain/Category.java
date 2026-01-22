package com.yogieat.domain.category.domain;

import com.yogieat.domain.category.domain.value.LargeCategory;

public record Category(
        Long id,
        LargeCategory largeCategory,
        String mediumCategory
) {
}
