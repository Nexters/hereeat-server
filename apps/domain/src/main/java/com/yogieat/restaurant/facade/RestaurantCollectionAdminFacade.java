package com.yogieat.restaurant.facade;

import com.yogieat.restaurant.service.RestaurantCollectionProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
        name = "restaurant.collection.processor.enabled",
        havingValue = "true",
        matchIfMissing = true
)
@RequiredArgsConstructor
public class RestaurantCollectionAdminFacade {

    private final RestaurantCollectionProcessor collectionProcessor;

    public void collectRestaurants() {
        collectionProcessor.collectAllRegions();
    }
}
