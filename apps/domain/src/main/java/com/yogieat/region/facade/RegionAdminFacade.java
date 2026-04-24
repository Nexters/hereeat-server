package com.yogieat.region.facade;

import com.yogieat.region.domain.RegionMaster;
import com.yogieat.region.domain.RegionSummary;
import com.yogieat.region.service.RegionCommand;
import com.yogieat.region.service.RegionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegionAdminFacade {

    private final RegionService regionService;

    @Transactional(readOnly = true)
    public RegionSummary getRegionById(Long regionId) {
        return regionService.getRegionSummaryById(regionId);
    }

    @Transactional(readOnly = true)
    public List<RegionSummary> getRegions(String province) {
        return regionService.findRegionDashboard(province);
    }

    @Transactional
    public RegionMaster createRegion(RegionCommand.Create command) {
        return regionService.createRegion(command);
    }

    @Transactional
    public RegionSummary updateRegion(Long regionId, RegionCommand.Patch command) {
        regionService.updateRegion(regionId, command);
        return regionService.getRegionSummaryById(regionId);
    }

    @Transactional
    public void deleteRegionById(Long regionId) {
        regionService.deleteRegionById(regionId);
    }
}
