package com.yogieat.category.domain;

import com.yogieat.category.domain.value.LargeCategory;
import java.time.LocalDateTime;

public record Category(
        Long id,
        LargeCategory largeCategory,
        String mediumCategory,
        LocalDateTime createdAt) {
}
