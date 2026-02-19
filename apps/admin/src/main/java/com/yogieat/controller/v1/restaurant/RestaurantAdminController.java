package com.yogieat.controller.v1.restaurant;

import com.yogieat.controller.v1.restaurant.response.RestaurantListResponse;
import com.yogieat.service.restaurant.RestaurantAdminService;
import com.yogieat.service.restaurant.result.RestaurantAdminListResult;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/restaurants")
@Validated
@RequiredArgsConstructor
public class RestaurantAdminController {

    private final RestaurantAdminService restaurantAdminService;

    @GetMapping
    public RestaurantListResponse getPageRestaurants(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Positive int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String largeCategory,
            @RequestParam(required = false) Long categoryId
    ) {
        RestaurantAdminListResult result = restaurantAdminService.getPageRestaurants(
                page,
                size,
                keyword,
                region,
                largeCategory,
                categoryId
        );
        return RestaurantListResponse.from(result);
    }
}
