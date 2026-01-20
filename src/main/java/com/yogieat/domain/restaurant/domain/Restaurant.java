package com.yogieat.domain.restaurant.domain;

import com.yogieat.domain.common.GeoJson;

public record Restaurant(
        Long id,
        Long categoryId,
        String name,
        String address,
        Double rating,
        String imageUrl,
        String mapUrl,
        String representativeReview,
        String description,
        GeoJson.Point location
){
}
