package com.yogieat.region.service;

import com.yogieat.common.GeoJson;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public final class RegionCommand {
    public record Create(
            String code,
            String displayName,
            GeoJson.Point coordinatesStandard,
            boolean active,
            Integer sortOrder
    ) {
    }
}
