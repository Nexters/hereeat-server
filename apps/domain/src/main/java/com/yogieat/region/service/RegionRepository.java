package com.yogieat.region.service;

import com.yogieat.region.domain.RegionMaster;
import java.util.List;
import java.util.Optional;

public interface RegionRepository {
    Optional<RegionMaster> findByCode(String code);

    List<RegionMaster> findAllActiveOrderBySortOrder();

    RegionMaster save(RegionMaster regionMaster);

    int backfillRestaurantRegionIds();

    int backfillGatheringRegionIds();
}
