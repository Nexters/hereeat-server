package com.yogieat.region.facade;

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

    public RegionSummary getRegionById(Long regionId) {
        return regionService.getRegionSummaryById(regionId);
    }

    public List<RegionSummary> getRegions() {
        return regionService.findRegionDashboard();
    }

    @Transactional
    public com.yogieat.region.domain.RegionMaster createRegion(RegionCommand.Create command) {
        return regionService.createRegion(command);
    }
}
