package com.yogieat.controller.v1.restaurant;

import com.yogieat.restaurant.facade.RestaurantCollectionAdminFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/restaurants/collections")
@ConditionalOnProperty(
        name = "restaurant.collection.processor.enabled",
        havingValue = "true",
        matchIfMissing = true
)
@RequiredArgsConstructor
public class RestaurantCollectionAdminController {

    private final RestaurantCollectionAdminFacade restaurantCollectionAdminFacade;

    @PostMapping
    public ResponseEntity<Void> collectRestaurants() {
        restaurantCollectionAdminFacade.collectRestaurants();
        return ResponseEntity.noContent().build();
    }
}
