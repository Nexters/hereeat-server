package com.yogieat.controller.v1.restaurant;

import com.yogieat.controller.v1.restaurant.response.RestaurantDetailResponse;
import com.yogieat.restaurant.facade.RestaurantFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/restaurants")
@RequiredArgsConstructor
public class RestaurantController {

    private final RestaurantFacade restaurantFacade;

    @GetMapping("/{restaurantId}")
    public RestaurantDetailResponse.Detail getRestaurantDetail(@PathVariable Long restaurantId) {
        return RestaurantDetailResponse.Detail.from(restaurantFacade.getRestaurantDetailBy(restaurantId));
    }
}
