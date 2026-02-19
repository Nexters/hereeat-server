package com.yogieat.service.restaurant;

import com.yogieat.category.domain.value.LargeCategory;
import com.yogieat.common.Region;
import com.yogieat.restaurant.domain.Restaurant;
import com.yogieat.restaurant.service.RestaurantAdminListCriteria;
import com.yogieat.restaurant.service.RestaurantService;
import com.yogieat.service.restaurant.result.RestaurantAdminListItemResult;
import com.yogieat.service.restaurant.result.RestaurantAdminListResult;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RestaurantAdminService {

    private final RestaurantService restaurantService;

    public RestaurantAdminListResult getPageRestaurants(
            int page,
            int size,
            String keyword,
            String region,
            String largeCategory,
            Long categoryId
    ) {
        Region regionValue = parseRegion(region);
        LargeCategory largeCategoryValue = parseLargeCategory(largeCategory);
        String normalizedKeyword = normalizeKeyword(keyword);
        RestaurantAdminListCriteria criteria = RestaurantAdminListCriteria.of(
                normalizedKeyword,
                regionValue,
                largeCategoryValue,
                categoryId
        );

        List<Restaurant> restaurants = restaurantService.findAdminRestaurants(
                criteria,
                page,
                size
        );
        long totalElements = restaurantService.countAdminRestaurantList(criteria);

        List<RestaurantAdminListItemResult> content = restaurants.stream()
                .map(RestaurantAdminListItemResult::from)
                .toList();

        return RestaurantAdminListResult.of(content, page, size, totalElements);
    }

    private Region parseRegion(String region) {
        if (region == null || region.isBlank()) {
            return null;
        }

        String normalizedRegion = region.strip();
        try {
            return Region.valueOf(normalizedRegion);
        } catch (IllegalArgumentException ignored) {
            return Arrays.stream(Region.values())
                    .filter(value -> value.name().equalsIgnoreCase(normalizedRegion)
                            || value.getName().equals(normalizedRegion))
                    .findFirst()
                    .orElse(null);
        }
    }

    private LargeCategory parseLargeCategory(String largeCategory) {
        if (largeCategory == null || largeCategory.isBlank()) {
            return null;
        }
        return LargeCategory.fromString(largeCategory.strip());
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }

        String normalizedKeyword = keyword.strip();
        return normalizedKeyword.isBlank() ? null : normalizedKeyword;
    }
}
