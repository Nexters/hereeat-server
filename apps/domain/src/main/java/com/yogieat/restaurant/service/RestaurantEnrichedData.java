package com.yogieat.restaurant.service;

import com.yogieat.category.domain.value.LargeCategory;
import com.yogieat.common.GeoJson;
import com.yogieat.gathering.domain.value.TimeSlot;
import com.yogieat.restaurant.domain.SuggestionRestaurant;
import java.time.LocalDate;
import java.util.List;

final class RestaurantEnrichedData {
    String externalId;
    String mapUrl;
    GeoJson.Point geoJsonLocation;
    Double rating;
    String imageUrl;
    String placeName;
    String representativeReview;
    Integer reviewCount;
    Integer blogReviewCount;
    String representMenu;
    Integer representMenuPrice;
    String priceLevel;
    String aiMateSummaryTitle;
    List<String> aiMateSummaryContents;
    TimeSlot timeSlot;
    // 카카오 API에서 추출한 카테고리 정보
    String apiCategoryName2;
    String apiCategoryName3;
    LargeCategory apiLargeCategory;
    String apiMediumCategory;
    List<LocalDate> offDays;
    boolean skip;

    private RestaurantEnrichedData() {
    }

    static RestaurantEnrichedData fromSuggestion(SuggestionRestaurant suggestion) {
        RestaurantEnrichedData data = new RestaurantEnrichedData();
        data.rating = suggestion.rating();
        return data;
    }

    static RestaurantEnrichedData skipped() {
        RestaurantEnrichedData data = new RestaurantEnrichedData();
        data.skip = true;
        return data;
    }

    String externalId() {
        return skip ? null : externalId;
    }

    String mapUrl() {
        return skip ? null : mapUrl;
    }

    GeoJson.Point geoJsonLocation() {
        return skip ? null : geoJsonLocation;
    }

    Double rating() {
        return skip ? null : rating;
    }

    String imageUrl() {
        return skip ? null : imageUrl;
    }

    String placeName() {
        return skip ? null : placeName;
    }

    String representativeReview() {
        return skip ? null : representativeReview;
    }

    Integer reviewCount() {
        return skip ? null : reviewCount;
    }

    Integer blogReviewCount() {
        return skip ? null : blogReviewCount;
    }

    String representMenu() {
        return skip ? null : representMenu;
    }

    Integer representMenuPrice() {
        return skip ? null : representMenuPrice;
    }

    String priceLevel() {
        return skip ? null : priceLevel;
    }

    String aiMateSummaryTitle() {
        return skip ? null : aiMateSummaryTitle;
    }

    List<String> aiMateSummaryContents() {
        return skip ? null : aiMateSummaryContents;
    }

    TimeSlot timeSlot() {
        return skip ? null : timeSlot;
    }

    LargeCategory apiLargeCategory() {
        return skip ? null : apiLargeCategory;
    }

    String apiCategoryName2() {
        return skip ? null : apiCategoryName2;
    }

    String apiCategoryName3() {
        return skip ? null : apiCategoryName3;
    }

    String apiMediumCategory() {
        return skip ? null : apiMediumCategory;
    }

    List<LocalDate> offDays() {
        return skip ? null : offDays;
    }

    boolean isSkipped() {
        return skip;
    }
}
