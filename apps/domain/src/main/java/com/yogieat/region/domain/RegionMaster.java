package com.yogieat.region.domain;

import com.yogieat.common.GeoJson;
import java.time.LocalDateTime;

public record RegionMaster(
        Long id,
        String code,
        String province,
        String displayName,
        GeoJson.Point coordinatesStandard,
        RegionStatus status,
        int sortOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
