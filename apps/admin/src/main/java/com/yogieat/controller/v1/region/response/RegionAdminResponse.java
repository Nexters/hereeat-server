package com.yogieat.controller.v1.region.response;

import com.yogieat.common.GeoJson;
import com.yogieat.region.domain.RegionMaster;
import com.yogieat.region.domain.RegionStatus;
import com.yogieat.region.domain.RegionSummary;
import java.util.List;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public final class RegionAdminResponse {

    public record ListResponse(List<RegionItemResponse> regions) {
        public static ListResponse from(List<RegionSummary> regions) {
            return new ListResponse(
                    regions.stream()
                            .map(RegionItemResponse::from)
                            .toList()
            );
        }
    }

    public record RegionItemResponse(
            Long id,
            String name,
            String province,
            String displayName,
            GeoJson.Point coordinatesStandard,
            RegionStatus status,
            int sortOrder,
            long restaurantCount
    ) {
        public static RegionItemResponse from(RegionSummary summary) {
            RegionMaster region = summary.region();
            return new RegionItemResponse(
                    region.id(),
                    region.code(),
                    region.province(),
                    region.displayName(),
                    region.coordinatesStandard(),
                    region.status(),
                    region.sortOrder(),
                    summary.restaurantCount()
            );
        }
    }

    public record CreateResponse(RegionItemResponse region) {
        public static CreateResponse from(RegionMaster region) {
            return new CreateResponse(
                    new RegionItemResponse(
                            region.id(),
                            region.code(),
                            region.province(),
                            region.displayName(),
                            region.coordinatesStandard(),
                            region.status(),
                            region.sortOrder(),
                            0L
                    )
            );
        }
    }

    public record DetailResponse(RegionItemResponse region) {
        public static DetailResponse from(RegionSummary region) {
            return new DetailResponse(RegionItemResponse.from(region));
        }
    }
}
