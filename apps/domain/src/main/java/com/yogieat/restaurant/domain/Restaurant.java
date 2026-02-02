package com.yogieat.restaurant.domain;

import com.yogieat.common.GeoJson;
import com.yogieat.common.Region;

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
