package com.yogieat.restaurant.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yogieat.common.GeoJson;
import com.yogieat.common.Region;
import com.yogieat.external.kakao.result.KakaoPlaceDetailData;
import com.yogieat.gathering.domain.value.TimeSlot;
import java.time.LocalDate;
import java.util.List;

public record CreateRestaurant(
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
        GeoJson.Point location,
        // 추천 근거 데이터 (신규 필드)
        Integer reviewCount,
        Integer blogReviewCount,
        String representMenu,
        Integer representMenuPrice,
        String priceLevel,
        String aiMateSummaryTitle,
        String aiMateSummaryContents,  // JSON 문자열로 저장
        // 추천 시간대 (신규 필드)
        TimeSlot timeSlot,
        // 휴무일
        String offDays  // JSON 문자열로 저장
) {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static CreateRestaurant of(
            SuggestionRestaurant suggestion,
            String placeName,
            Long categoryId,
            String externalId,
            String mapUrl,
            GeoJson.Point location,
            Double rating,
            String imageUrl,
            String representativeReview,
            Region region,
            // 추천 근거 데이터 파라미터
            Integer reviewCount,
            Integer blogReviewCount,
            String representMenu,
            Integer representMenuPrice,
            String priceLevel,
            String aiMateSummaryTitle,
            List<String> aiMateSummaryContents,
            // 추천 시간대 파라미터
            TimeSlot timeSlot,
            // 휴무일 파라미터
            List<LocalDate> offDays
    ) {
        return new CreateRestaurant(
                externalId,
                categoryId,
                placeName,
                suggestion.address(),
                rating,
                imageUrl,
                mapUrl,
                representativeReview != null ? representativeReview : suggestion.representativeReview(),
                suggestion.description(),
                region,
                location,
                reviewCount,
                blogReviewCount,
                representMenu,
                representMenuPrice,
                priceLevel,
                aiMateSummaryTitle,
                toJson(aiMateSummaryContents),
                timeSlot,
                offDaysToJson(offDays)
        );
    }

    private static String toJson(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static String offDaysToJson(List<LocalDate> offDays) {
        if (offDays == null) {
            return null;
        }
        if (offDays.isEmpty()) {
            return "[]";
        }
        return toJson(offDays.stream().map(LocalDate::toString).toList());
    }

    public static CreateRestaurant fromKakaoPlaceDetail(
            KakaoPlaceDetailData detail,
            Long categoryId,
            String externalId,
            Region region,
            String description
    ) {
        GeoJson.Point location = toPoint(detail);
        String mapUrl = externalId == null || externalId.isBlank()
                ? null
                : "https://place.map.kakao.com/" + externalId;

        String normalizedDescription = (description == null || description.isBlank()) ? null : description.strip();

        return new CreateRestaurant(
                externalId,
                categoryId,
                detail == null ? null : detail.placeName(),
                detail == null ? null : detail.address(),
                detail == null ? null : detail.rating(),
                detail == null ? null : detail.mainPhotoUrl(),
                mapUrl,
                detail == null ? null : detail.representativeReview(),
                normalizedDescription,
                region,
                location,
                detail == null ? null : detail.reviewCount(),
                detail == null ? null : detail.blogReviewCount(),
                detail == null ? null : detail.representMenu(),
                detail == null ? null : detail.representMenuPrice(),
                detail == null ? null : detail.priceLevel(),
                detail == null ? null : detail.aiMateSummaryTitle(),
                toJson(detail == null ? null : detail.aiMateSummaryContents()),
                detail == null ? null : detail.timeSlot(),
                offDaysToJson(detail == null ? null : detail.offDays())
        );
    }

    private static GeoJson.Point toPoint(KakaoPlaceDetailData detail) {
        if (detail == null || detail.latitude() == null || detail.longitude() == null) {
            return null;
        }
        return new GeoJson.Point(List.of(detail.longitude(), detail.latitude()));
    }
}
