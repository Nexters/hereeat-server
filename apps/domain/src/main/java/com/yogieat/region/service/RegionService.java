package com.yogieat.region.service;

import com.yogieat.common.Region;
import com.yogieat.region.domain.RegionMaster;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegionService {

    private final RegionRepository regionRepository;

    @Transactional(readOnly = true)
    public List<RegionMaster> findActiveRegions() {
        return regionRepository.findAllActiveOrderBySortOrder().stream()
                .filter(regionMaster -> Region.fromString(regionMaster.code()) != null)
                .toList();
    }
}
