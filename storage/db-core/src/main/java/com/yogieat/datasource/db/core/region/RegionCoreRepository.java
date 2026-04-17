package com.yogieat.datasource.db.core.region;

import com.yogieat.region.domain.RegionMaster;
import com.yogieat.region.service.RegionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RegionCoreRepository implements RegionRepository {

    private final RegionJpaRepository regionJpaRepository;

    @Override
    public List<RegionMaster> findAllActiveOrderBySortOrder() {
        return regionJpaRepository.findAllByActiveTrueOrderBySortOrderAsc().stream()
                .map(RegionEntity::toDomain)
                .toList();
    }
}
