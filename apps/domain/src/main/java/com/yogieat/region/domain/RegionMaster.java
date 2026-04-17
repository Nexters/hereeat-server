package com.yogieat.region.domain;

import com.yogieat.common.GeoJson;
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
}
