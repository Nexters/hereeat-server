package com.yogieat.restaurant.facade;

import com.yogieat.category.domain.value.LargeCategory;
import com.yogieat.common.Region;
import com.yogieat.restaurant.domain.Restaurant;
import com.yogieat.restaurant.result.RestaurantAdminListItemResult;
import com.yogieat.restaurant.result.RestaurantAdminListResult;
import com.yogieat.restaurant.result.RestaurantAdminResult;
import com.yogieat.restaurant.service.RestaurantAdminListCriteria;
import com.yogieat.restaurant.service.RestaurantCommand;
import com.yogieat.restaurant.service.RestaurantService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RestaurantAdminFacade {

    private final RestaurantService restaurantService;

    public RestaurantAdminListResult getPageRestaurants(
            int page,
            int size,
            String keyword,
            String region,
            String largeCategory,
            Long categoryId
    ) {
        Region regionValue = Region.fromString(region);
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

    public RestaurantAdminResult.Detail getRestaurantBy(Long restaurantId) {
        Restaurant restaurant = restaurantService.getBy(restaurantId);
        return RestaurantAdminResult.Detail.from(restaurant);
    }

    @Transactional
    public RestaurantAdminResult.Detail updateRestaurant(
            Long restaurantId,
            RestaurantCommand.Patch command
    ) {
        Restaurant restaurant = restaurantService.updateBy(restaurantId, command);
        return RestaurantAdminResult.Detail.from(restaurant);
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
