package com.yogieat.domain.restaurant.domain;

public record Restaurant(
        Long id,
        String name,
        String address,
        Double rating,
        String imageUrl,
        String mapUrl,
        String largeCategory,
        String mediumCategory,
        String representativeReview,
        String description
){
}
