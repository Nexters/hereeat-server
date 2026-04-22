package com.yogieat.region.domain;

public record RegionSummary(
        RegionMaster region,
        long restaurantCount
) {
}
