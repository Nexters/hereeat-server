package com.yogieat.controller.v1.restaurant;

import com.yogieat.controller.v1.restaurant.response.RestaurantDetailResponse;
import com.yogieat.restaurant.facade.RestaurantFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "🧑‍🍳 Restaurant API", description = "맛집 관련 API")
@RestController
@RequestMapping("/api/v1/restaurants")
@RequiredArgsConstructor
public class RestaurantController {

    private final RestaurantFacade restaurantFacade;

    @Operation(summary = "맛집 상세 조회", description = "맛집 ID로 상세 정보를 조회합니다.")
    @GetMapping("/{restaurantId}")
    public RestaurantDetailResponse.Detail getRestaurantDetail(@PathVariable Long restaurantId) {
        return RestaurantDetailResponse.Detail.from(restaurantFacade.getRestaurantDetailBy(restaurantId));
    }
}
