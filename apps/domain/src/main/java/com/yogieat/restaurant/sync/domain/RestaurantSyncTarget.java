package com.yogieat.restaurant.sync.domain;

import com.yogieat.common.GeoJson;
import com.yogieat.common.Region;

public record RestaurantSyncTarget(
        Long id,
        String name,
        Region region,
        String externalId,
        GeoJson.Point location
) {
    public RestaurantSyncTarget(
            Long id,
            String name,
            Region region,
            String externalId
    ) {
        this(id, name, region, externalId, null);
    }
}
