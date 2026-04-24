package com.yogieat.controller.v1.region;

import com.yogieat.controller.v1.region.request.RegionAdminRequest;
import com.yogieat.controller.v1.region.response.RegionAdminResponse;
import com.yogieat.region.facade.RegionAdminFacade;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/regions")
@Validated
@RequiredArgsConstructor
public class RegionAdminController {

    private final RegionAdminFacade regionAdminFacade;

    @GetMapping
    public RegionAdminResponse.ListResponse getRegions(
            @RequestParam(required = false) String province
    ) {
        return RegionAdminResponse.ListResponse.from(
                regionAdminFacade.getRegions(StringUtils.hasText(province) ? province.trim() : null)
        );
    }

    @GetMapping("/{regionId}")
    public RegionAdminResponse.DetailResponse getRegion(
            @PathVariable Long regionId
    ) {
        return RegionAdminResponse.DetailResponse.from(regionAdminFacade.getRegionById(regionId));
    }

    @PostMapping
    public ResponseEntity<RegionAdminResponse.CreateResponse> createRegion(
            @Valid @RequestBody RegionAdminRequest.Create request
    ) {
        var createdRegion = regionAdminFacade.createRegion(RegionAdminRequest.Create.toCommand(request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(RegionAdminResponse.CreateResponse.from(createdRegion));
    }

    @PatchMapping("/{regionId}")
    public RegionAdminResponse.DetailResponse updateRegion(
            @PathVariable Long regionId,
            @RequestBody RegionAdminRequest.Patch request
    ) {
        return RegionAdminResponse.DetailResponse.from(
                regionAdminFacade.updateRegion(regionId, RegionAdminRequest.Patch.toCommand(request))
        );
    }

    @DeleteMapping("/{regionId}")
    public ResponseEntity<Void> deleteRegion(
            @PathVariable Long regionId
    ) {
        regionAdminFacade.deleteRegionById(regionId);
        return ResponseEntity.noContent().build();
    }
}
