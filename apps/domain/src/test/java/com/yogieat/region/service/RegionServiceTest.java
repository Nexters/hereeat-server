package com.yogieat.region.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.yogieat.common.GeoJson;
import com.yogieat.region.domain.RegionMaster;
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
    void findCollectionRegionSummaries_returns_inactive_regions_for_collection() {
        RegionSummary activeRegion = summary(1L, "GANGNAM", "강남역", true);
        RegionSummary inactiveRegion = summary(2L, "SEONGSU", "성수역", false);

        when(regionRepository.findAllRegionSummariesOrderBySortOrder())
                .thenReturn(List.of(activeRegion, inactiveRegion));

        List<RegionSummary> result = regionService.findCollectionRegionSummaries();

        assertThat(result)
                .extracting(summary -> summary.region().displayName())
                .containsExactly("강남역", "성수역");
    }

    private RegionSummary summary(Long id, String code, String displayName, boolean active) {
        return new RegionSummary(
                new RegionMaster(
                        id,
                        code,
                        "서울",
                        displayName,
                        new GeoJson.Point(List.of(127.0, 37.0)),
                        active,
                        id.intValue(),
                        null,
                        null
                ),
                0
        );
    }
}
