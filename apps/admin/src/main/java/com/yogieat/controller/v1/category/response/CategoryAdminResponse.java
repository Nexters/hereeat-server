package com.yogieat.controller.v1.category.response;

import com.yogieat.category.domain.Category;
import java.text.Collator;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class CategoryAdminResponse {

    private CategoryAdminResponse() {
    }

    public record ListResponse(List<CategoryItemResponse> categories) {
        public static ListResponse from(List<Category> categories) {
            Collator collator = Collator.getInstance(Locale.KOREAN);
            collator.setStrength(Collator.PRIMARY);

            return new ListResponse(
                    categories.stream()
                            .sorted(Comparator.comparing(
                                            Category::mediumCategory,
                                            Comparator.nullsLast(collator)
                                    )
                                    .thenComparing(Category::id, Comparator.nullsLast(Long::compareTo)))
                            .map(CategoryItemResponse::from)
                            .toList()
            );
        }
    }

    public record CategoryItemResponse(
            Long id,
            String largeCategory,
            String mediumCategory,
            LocalDateTime createdAt
    ) {
        public static CategoryItemResponse from(Category category) {
            return new CategoryItemResponse(
                    category.id(),
                    category.largeCategory() != null ? category.largeCategory().name() : null,
                    category.mediumCategory(),
                    category.createdAt()
            );
        }
    }
}
