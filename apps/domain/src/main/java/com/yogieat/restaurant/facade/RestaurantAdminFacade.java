package com.yogieat.restaurant.facade;

import com.yogieat.category.domain.value.LargeCategory;
import com.yogieat.common.Region;
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

        List<RestaurantAdminListItemResult> content = restaurantService.findAdminRestaurants(
                criteria,
                page,
                size
        );
        long totalElements = restaurantService.countAdminRestaurantList(criteria);

        return RestaurantAdminListResult.of(content, page, size, totalElements);
    }

    public RestaurantAdminResult.Detail getRestaurantBy(Long restaurantId) {
        return restaurantService.getAdminRestaurantDetailBy(restaurantId);
    }

    @Transactional
    public RestaurantAdminResult.Detail updateRestaurant(
            Long restaurantId,
            RestaurantCommand.Patch command
    ) {
        restaurantService.updateBy(restaurantId, command);
        return restaurantService.getAdminRestaurantDetailBy(restaurantId);
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
