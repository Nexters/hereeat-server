package com.yogieat.controller.v1.region;

import com.yogieat.controller.v1.region.response.RegionAdminResponse;
import com.yogieat.region.facade.RegionAdminFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/regions")
@Validated
@RequiredArgsConstructor
public class RegionAdminController {

    private final RegionAdminFacade regionAdminFacade;

    @GetMapping
    public RegionAdminResponse.ListResponse getRegions() {
        return RegionAdminResponse.ListResponse.from(regionAdminFacade.getRegions());
    }
}
