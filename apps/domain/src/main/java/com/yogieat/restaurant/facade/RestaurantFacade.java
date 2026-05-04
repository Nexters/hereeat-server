package com.yogieat.restaurant.facade;

import com.yogieat.restaurant.result.RestaurantDetailResult;
import com.yogieat.restaurant.service.RestaurantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RestaurantFacade {

    private final RestaurantService restaurantService;

    @Transactional(readOnly = true)
    public RestaurantDetailResult getRestaurantDetailBy(Long restaurantId) {
        return restaurantService.getRestaurantDetailBy(restaurantId);
    }
}
