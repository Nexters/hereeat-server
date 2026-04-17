package com.yogieat.region.facade;

import com.yogieat.region.domain.RegionMaster;
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

    public List<RegionMaster> getRegions() {
        return regionService.findActiveRegionsForAdmin();
    }
}
