package com.yogieat.controller.v1.region.response;

import com.yogieat.common.GeoJson;
import com.yogieat.region.domain.RegionMaster;
import java.util.List;

public final class RegionAdminResponse {

    private RegionAdminResponse() {
    }

    public record ListResponse(List<RegionItemResponse> regions) {
        public static ListResponse from(List<RegionMaster> regions) {
            return new ListResponse(
                    regions.stream()
                            .map(RegionItemResponse::from)
                            .toList()
            );
        }
    }

    public record RegionItemResponse(
            String name,
            String displayName,
            GeoJson.Point coordinatesStandard
    ) {
        public static RegionItemResponse from(RegionMaster region) {
            return new RegionItemResponse(
                    region.code(),
                    region.displayName(),
                    region.coordinatesStandard()
            );
        }
    }
}
