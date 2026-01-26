package com.yogieat.domain.restaurant.domain;

import com.yogieat.domain.common.GeoJson;
import com.yogieat.domain.common.Region;

public record Restaurant(
        Long id,
        String externalId,
        Long categoryId,
        String name,
        String address,
        Double rating,
        String imageUrl,
        String mapUrl,
        String representativeReview,
        String description,
        Region region,
        GeoJson.Point location
){
}
