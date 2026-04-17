package com.yogieat.region.service;

import com.yogieat.region.domain.RegionMaster;
import java.util.List;

public interface RegionRepository {
    List<RegionMaster> findAllActiveOrderBySortOrder();
}
