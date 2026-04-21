package com.yogieat.region.service;

import com.yogieat.region.domain.RegionMaster;
import com.yogieat.region.domain.RegionSummary;
import java.util.List;
import java.util.Optional;

public interface RegionRepository {
    Optional<RegionMaster> findById(Long id);
    Optional<RegionSummary> findRegionSummaryById(Long id);
    List<RegionMaster> findAllOrderBySortOrder();
    List<RegionMaster> findAllActiveOrderBySortOrder();
    List<RegionSummary> findAllRegionSummariesOrderBySortOrder();
    List<RegionSummary> findAllActiveRegionSummariesOrderBySortOrder();
    Optional<RegionMaster> findActiveByDisplayName(String displayName);
    boolean existsByCode(String code);
    boolean existsByDisplayName(String displayName);
    int nextSortOrder();
    RegionMaster save(RegionMaster regionMaster);
}
