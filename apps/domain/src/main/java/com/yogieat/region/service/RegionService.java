package com.yogieat.region.service;

import com.yogieat.common.Region;
import com.yogieat.region.domain.RegionMaster;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegionService {

    private final RegionRepository regionRepository;

    @Transactional(readOnly = true)
    public List<RegionMaster> findActiveRegions() {
        return regionRepository.findAllActiveOrderBySortOrder().stream()
                .filter(regionMaster -> Region.fromString(regionMaster.code()) != null)
                .toList();
    }

    @Transactional
    public void syncRegionsFromEnumAndBackfillLegacyReferences() {
        syncRegionsFromEnum();
        int restaurantBackfilled = regionRepository.backfillRestaurantRegionIds();
        int gatheringBackfilled = regionRepository.backfillGatheringRegionIds();

        log.info(
                "Completed region startup sync: restaurantRegionIdsBackfilled={}, gatheringRegionIdsBackfilled={}",
                restaurantBackfilled,
                gatheringBackfilled
        );
    }

    private void syncRegionsFromEnum() {
        Region[] regions = Region.values();
        for (int index = 0; index < regions.length; index++) {
            Region region = regions[index];
            int sortOrder = index;
            RegionMaster regionMaster = regionRepository.findByCode(region.name())
                    .map(existing -> existing.syncFromEnum(region))
                    .orElseGet(() -> RegionMaster.seededFrom(region, sortOrder));

            regionRepository.save(regionMaster);
        }

        log.info("Synchronized {} region master rows from enum definitions", regions.length);
    }
}
