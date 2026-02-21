package com.yogieat.controller.v1.region.response;

import com.yogieat.common.GeoJson;
import com.yogieat.common.Region;
import java.util.Arrays;
import java.util.List;

public final class RegionAdminResponse {

    private RegionAdminResponse() {
    }

    public record ListResponse(List<RegionItemResponse> regions) {
        public static ListResponse from(Region[] regions) {
            return new ListResponse(
                    Arrays.stream(regions)
                            .map(RegionItemResponse::from)
                            .toList()
            );
        }
    }

    public record RegionItemResponse(
            String name,
            GeoJson.Point coordinatesStandard
    ) {
        public static RegionItemResponse from(Region region) {
            return new RegionItemResponse(region.getName(), region.getCoordinatesStandard());
        }
    }
}
