package com.yogieat.controller.v1.region;

import com.yogieat.controller.v1.region.response.GetRegionsResponse;
import com.yogieat.region.service.RegionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "📍 Region API", description = "지역 관련 API")
@RestController
@RequestMapping("/api/v1/regions")
@RequiredArgsConstructor
public class RegionController {

    private final RegionService regionService;

    @GetMapping
    public GetRegionsResponse getRegions() {
        return GetRegionsResponse.from(regionService.findActiveRegions());
    }
}
