package com.yogieat.region.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.yogieat.common.GeoJson;
import com.yogieat.region.domain.RegionMaster;
import com.yogieat.region.domain.RegionStatus;
import com.yogieat.region.domain.RegionSummary;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegionServiceTest {

    @Mock
    private RegionRepository regionRepository;

    @Mock
    private RegionValidator regionValidator;

    @InjectMocks
    private RegionService regionService;

    @Test
    void findActiveRegions_returns_db_only_regions() {
        RegionMaster activeRegion = region(1L, "GANGNAM", "강남역", RegionStatus.ACTIVE);
        RegionMaster dbOnlyRegion = region(2L, "YEOKSAM", "역삼역", RegionStatus.ACTIVE);

        when(regionRepository.findAllActiveOrderBySortOrder())
                .thenReturn(List.of(dbOnlyRegion, activeRegion));

        List<RegionMaster> result = regionService.findActiveRegions();

        assertThat(result)
                .extracting(RegionMaster::code)
                .containsExactly("YEOKSAM", "GANGNAM");
    }

    @Test
    void findCollectionRegionSummaries_returns_inactive_regions_for_collection() {
        RegionSummary activeRegion = summary(1L, "GANGNAM", "강남역", RegionStatus.ACTIVE);
        RegionSummary inactiveRegion = summary(2L, "SEONGSU", "성수역", RegionStatus.INACTIVE);

        when(regionRepository.findAllRegionSummariesOrderBySortOrder())
                .thenReturn(List.of(activeRegion, inactiveRegion));

        List<RegionSummary> result = regionService.findCollectionRegionSummaries();

        assertThat(result)
                .extracting(summary -> summary.region().displayName())
                .containsExactly("강남역", "성수역");
    }

    private RegionSummary summary(Long id, String code, String displayName, RegionStatus status) {
        return new RegionSummary(region(id, code, displayName, status), 0);
    }

    private RegionMaster region(Long id, String code, String displayName, RegionStatus status) {
        return new RegionMaster(
                id,
                code,
                "서울",
                displayName,
                new GeoJson.Point(List.of(127.0, 37.0)),
                status,
                id.intValue(),
                null,
                null
        );
    }
}
