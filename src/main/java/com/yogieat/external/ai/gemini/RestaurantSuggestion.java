package com.yogieat.external.ai.gemini;

public record RestaurantSuggestion(
    String name,
    String address,
    Double rating,
    String largeCategory,
    String mediumCategory,
    String description,
    String representativeReview
) {}
