package com.yogieat.region.service;

import com.yogieat.common.GeoJson;
import com.yogieat.region.domain.RegionStatus;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public final class RegionCommand {
    public record Create(
            String code,
            String province,
            String displayName,
            GeoJson.Point coordinatesStandard,
            RegionStatus status,
            Integer sortOrder
    ) {
    }

    public record Patch(
            String code,
            String province,
            String displayName,
            GeoJson.Point coordinatesStandard,
            RegionStatus status,
            Integer sortOrder
    ) {
        public static Patch empty() {
            return new Patch(null, null, null, null, null, null);
        }
    }
}
