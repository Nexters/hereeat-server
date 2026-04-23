package com.yogieat.controller.v1.region.response;

import com.yogieat.common.GeoJson;
import com.yogieat.region.domain.RegionMaster;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "지역 목록 조회 응답")
public record GetRegionsResponse(
        @Schema(description = "지역 목록")
        List<RegionItemResponse> regions
) {
    public static GetRegionsResponse from(List<RegionMaster> regions) {
        return new GetRegionsResponse(
                regions.stream()
                        .map(RegionItemResponse::from)
                        .toList()
        );
    }

    public record RegionItemResponse(
            @Schema(description = "지역 코드", example = "GANGNAM")
            String code,
            @Schema(description = "시도 구분", example = "서울")
            String province,
            @Schema(description = "지역 표시명", example = "강남역")
            String displayName,
            @Schema(description = "지역 기준 좌표")
            GeoJson.Point coordinatesStandard
    ) {
        public static RegionItemResponse from(RegionMaster region) {
            return new RegionItemResponse(
                    region.code(),
                    region.province(),
                    region.displayName(),
                    region.coordinatesStandard()
            );
        }
    }
}
