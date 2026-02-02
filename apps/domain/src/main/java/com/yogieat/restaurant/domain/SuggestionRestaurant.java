package com.yogieat.restaurant.domain;

public record SuggestionRestaurant(
    String name,
    String address,
    Double rating,
    String largeCategory,
    String mediumCategory,
    String description,
    String representativeReview
) {}
