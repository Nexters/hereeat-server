package com.yogieat.restaurant.sync.domain;

import com.yogieat.common.GeoJson;
import com.yogieat.common.Region;

public record RestaurantSyncTarget(
        Long id,
        String name,
        String regionCode,
        String regionDisplayName,
        GeoJson.Point regionCoordinatesStandard,
        String externalId,
        GeoJson.Point location
) {
    public RestaurantSyncTarget(
            Long id,
            String name,
            Region region,
            String externalId,
            GeoJson.Point location
    ) {
        this(
                id,
                name,
                region == null ? null : region.name(),
                region == null ? null : region.getName(),
                region == null ? null : region.getCoordinatesStandard(),
                externalId,
                location
        );
    }

    public RestaurantSyncTarget(
            Long id,
            String name,
            Region region,
            String externalId
    ) {
        this(id, name, region, externalId, null);
    }
}
