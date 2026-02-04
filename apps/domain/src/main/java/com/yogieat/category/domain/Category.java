package com.yogieat.category.domain;

import com.yogieat.category.domain.value.LargeCategory;

public record Category(
        Long id,
        LargeCategory largeCategory,
        String mediumCategory
) {
}
