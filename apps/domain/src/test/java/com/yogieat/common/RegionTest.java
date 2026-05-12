package com.yogieat.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RegionTest {

    @Test
    void fromString_shouldCreateCodeOnlyRegion() {
        Region region = Region.fromString("gangnam");

        assertThat(region.name()).isEqualTo("GANGNAM");
        assertThat(region.getName()).isNull();
        assertThat(region.getCoordinatesStandard()).isNull();
    }
}
