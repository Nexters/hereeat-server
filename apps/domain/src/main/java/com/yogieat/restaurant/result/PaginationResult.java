package com.yogieat.restaurant.result;

public record PaginationResult(
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    public static PaginationResult of(int page, int size, long totalElements) {
        int totalPages = calculateTotalPages(totalElements, size);

        return new PaginationResult(
                page,
                size,
                totalElements,
                totalPages,
                hasNext(page, totalPages)
        );
    }

    private static int calculateTotalPages(long totalElements, int size) {
        if (size <= 0) {
            return 0;
        }
        return (int) ((totalElements + size - 1) / size);
    }

    private static boolean hasNext(int page, int totalPages) {
        if (totalPages <= 0) {
            return false;
        }
        return page + 1 < totalPages;
    }
}
