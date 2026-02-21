package com.yogieat.controller.v1.category.response;

import com.yogieat.category.domain.Category;
import java.time.LocalDateTime;
import java.util.List;

public final class CategoryAdminResponse {

    private CategoryAdminResponse() {
    }

    public record ListResponse(List<CategoryItemResponse> categories) {
        public static ListResponse from(List<Category> categories) {
            return new ListResponse(
                    categories.stream()
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
