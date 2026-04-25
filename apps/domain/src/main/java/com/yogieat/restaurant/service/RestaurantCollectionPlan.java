package com.yogieat.restaurant.service;

import com.yogieat.region.domain.RegionSummary;
import java.util.List;

final class RestaurantCollectionPlan {

    static final int REGION_RESTAURANT_LIMIT = 100;
    private static final int MIN_RESTAURANTS_PER_CATEGORY_REQUEST = 1;
    private static final int MAX_RESTAURANTS_PER_CATEGORY_REQUEST = 20;

    private RestaurantCollectionPlan() {
    }

    static List<Request> create(List<RegionSummary> regionSummaries, int categoryCount) {
        int safeCategoryCount = Math.max(categoryCount, 1);

        return regionSummaries.stream()
                .map(summary -> toRequest(summary, safeCategoryCount))
                .filter(request -> request.remainingSlots() > 0)
                .toList();
    }

    private static Request toRequest(RegionSummary summary, int categoryCount) {
        int currentCount = (int) Math.min(summary.restaurantCount(), REGION_RESTAURANT_LIMIT);
        int remainingSlots = REGION_RESTAURANT_LIMIT - currentCount;
        int countPerCategory = clamp(ceilDiv(remainingSlots, categoryCount));
        return new Request(summary, countPerCategory, remainingSlots);
    }

    private static int ceilDiv(int dividend, int divisor) {
        return (dividend + divisor - 1) / divisor;
    }

    private static int clamp(int count) {
        return Math.clamp(count,
                MIN_RESTAURANTS_PER_CATEGORY_REQUEST, MAX_RESTAURANTS_PER_CATEGORY_REQUEST);
    }

    record Request(
            RegionSummary regionSummary,
            int countPerCategory,
            int remainingSlots
    ) {
    }
}
