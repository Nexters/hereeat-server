package com.yogieat.external.ai.gemini;

import org.jspecify.annotations.NonNull;

/**
 * Key for grouping restaurant suggestions by location and category
 * Used in batch processing to organize API responses
 */
public record LocationCategoryKey(
    String location,
    String category
) {
    @Override
    public @NonNull String toString() {
        return location + "-" + category;
    }
}
