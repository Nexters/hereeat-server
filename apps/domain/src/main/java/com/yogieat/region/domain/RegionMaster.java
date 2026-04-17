package com.yogieat.region.domain;

import com.yogieat.common.GeoJson;
import com.yogieat.common.Region;
import java.time.LocalDateTime;

public record RegionMaster(
        Long id,
        String code,
        String displayName,
        GeoJson.Point coordinatesStandard,
        boolean active,
        int sortOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static RegionMaster seededFrom(Region region, int sortOrder) {
        return new RegionMaster(
                null,
                region.name(),
                region.getName(),
                region.getCoordinatesStandard(),
                true,
                sortOrder,
                null,
                null
        );
    }

    public RegionMaster syncFromEnum(Region region) {
        return new RegionMaster(
                id,
                region.name(),
                region.getName(),
                region.getCoordinatesStandard(),
                active,
                sortOrder,
                createdAt,
                updatedAt
        );
    }
}
