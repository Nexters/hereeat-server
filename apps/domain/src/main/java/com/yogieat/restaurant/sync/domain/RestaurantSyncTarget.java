package com.yogieat.restaurant.sync.domain;

import com.yogieat.common.Region;

public record RestaurantSyncTarget(
        Long id,
        String name,
        Region region,
        String externalId
) {
}
