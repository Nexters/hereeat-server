package com.yogieat.controller.v1.restaurant.request;

import com.yogieat.common.GeoJson;
import com.yogieat.common.Region;
import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import com.yogieat.gathering.domain.value.TimeSlot;
import com.yogieat.restaurant.service.RestaurantCommand;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public final class RestaurantRequest {

    private RestaurantRequest() {
    }

    public record Create(
            @NotBlank String externalId,
            @NotNull @Min(1) Long categoryId,
            @NotBlank String region
    ) {
        public static Create from(String externalId, Long categoryId, String region) {
            return new Create(externalId, categoryId, region);
        }

        public static RestaurantCommand.Create toCommand(Create request) {
            if (request == null) {
                throw new CustomException(ErrorCode.METHOD_ARGUMENT_TYPE_MISMATCH);
            }

            return new RestaurantCommand.Create(
                    trimOrNull(request.externalId()),
                    request.categoryId(),
                    parseRegion(request.region())
            );
        }
    }

    public record Patch(
            String externalId,
            Long categoryId,
            String name,
            String address,
            Double rating,
            String imageUrl,
            String mapUrl,
            String representativeReview,
            String description,
            String region,
            RestaurantLocationRequest location,
            Integer reviewCount,
            Integer blogReviewCount,
            String representMenu,
            Integer representMenuPrice,
            String priceLevel,
            String aiMateSummaryTitle,
            List<String> aiMateSummaryContents,
            String timeSlot
    ) {
        public static Patch from(
                String externalId,
                Long categoryId,
                String name,
                String address,
                Double rating,
                String imageUrl,
                String mapUrl,
                String representativeReview,
                String description,
                String region,
                RestaurantLocationRequest location,
                Integer reviewCount,
                Integer blogReviewCount,
                String representMenu,
                Integer representMenuPrice,
                String priceLevel,
                String aiMateSummaryTitle,
                List<String> aiMateSummaryContents,
                String timeSlot
        ) {
            return new Patch(
                    externalId,
                    categoryId,
                    name,
                    address,
                    rating,
                    imageUrl,
                    mapUrl,
                    representativeReview,
                    description,
                    region,
                    location,
                    reviewCount,
                    blogReviewCount,
                    representMenu,
                    representMenuPrice,
                    priceLevel,
                    aiMateSummaryTitle,
                    aiMateSummaryContents,
                    timeSlot
            );
        }

        public static Patch of() {
            return new Patch(
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

        public static RestaurantCommand.Patch toCommand(Patch request) {
            if (request == null) {
                return RestaurantCommand.Patch.empty();
            }

            return new RestaurantCommand.Patch(
                    trimOrNull(request.externalId()),
                    trimOrNull(request.name()),
                    trimOrNull(request.address()),
                    request.categoryId(),
                    parseRegion(request.region()),
                    request.rating(),
                    trimOrNull(request.imageUrl()),
                    trimOrNull(request.mapUrl()),
                    trimOrNull(request.representativeReview()),
                    trimOrNull(request.description()),
                    parseLocation(request.location()),
                    request.reviewCount(),
                    request.blogReviewCount(),
                    trimOrNull(request.representMenu()),
                    request.representMenuPrice(),
                    trimOrNull(request.priceLevel()),
                    trimOrNull(request.aiMateSummaryTitle()),
                    trimAiMateSummaryContents(request.aiMateSummaryContents()),
                    parseTimeSlot(request.timeSlot())
            );
        }
    }

    private static String trimOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    private static Region parseRegion(String region) {
        if (region == null || region.isBlank()) {
            return null;
        }

        Region parsed = Region.fromString(region);
        if (parsed == null) {
            throw new CustomException(ErrorCode.INVALID_LOCATION_NAME);
        }

        return parsed;
    }

    private static TimeSlot parseTimeSlot(String timeSlot) {
        if (timeSlot == null || timeSlot.isBlank()) {
            return null;
        }

        try {
            return TimeSlot.valueOf(timeSlot.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.METHOD_ARGUMENT_TYPE_MISMATCH);
        }
    }

    private static GeoJson.Point parseLocation(RestaurantLocationRequest location) {
        if (location == null || location.coordinates() == null) {
            return null;
        }

        List<Double> coordinates = location.coordinates();
        if (coordinates.size() != 2) {
            throw new CustomException(ErrorCode.METHOD_ARGUMENT_TYPE_MISMATCH);
        }

        Double longitude = coordinates.getFirst();
        Double latitude = coordinates.get(1);
        if (longitude == null || latitude == null) {
            throw new CustomException(ErrorCode.METHOD_ARGUMENT_TYPE_MISMATCH);
        }

        return new GeoJson.Point(List.of(longitude, latitude));
    }

    private static List<String> trimAiMateSummaryContents(List<String> aiMateSummaryContents) {
        if (aiMateSummaryContents == null) {
            return null;
        }
        List<String> cleanedList = aiMateSummaryContents.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::strip)
                .filter(s -> !s.isBlank())
                .toList();
        return cleanedList.isEmpty() ? null : cleanedList;
    }

    public record RestaurantLocationRequest(List<Double> coordinates) {
        public static RestaurantLocationRequest of(List<Double> coordinates) {
            return new RestaurantLocationRequest(coordinates);
        }
    }
}
