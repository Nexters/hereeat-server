package com.yogieat.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RegionTest {

    @Test
    void fromString_shouldResolveDefaultRegionMetadata() {
        Region region = Region.fromString("gangnam");

        assertThat(region.name()).isEqualTo("GANGNAM");
        assertThat(region.getName()).isEqualTo("강남역");
        assertThat(region.getCoordinatesStandard().getCoordinates()).containsExactly(127.0276, 37.4979);
    }

    @Test
    void fromString_shouldKeepUnknownRegionAsCodeOnly() {
        Region region = Region.fromString("custom");

        assertThat(region.name()).isEqualTo("CUSTOM");
        assertThat(region.getName()).isNull();
        assertThat(region.getCoordinatesStandard()).isNull();
    }
}
