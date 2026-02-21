package com.yogieat.controller.v1.region;

import com.yogieat.common.Region;
import com.yogieat.controller.v1.region.response.RegionAdminResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/regions")
@Validated
public class RegionAdminController {

    @GetMapping
    public RegionAdminResponse.ListResponse getRegions() {
        return RegionAdminResponse.ListResponse.from(Region.values());
    }
}
