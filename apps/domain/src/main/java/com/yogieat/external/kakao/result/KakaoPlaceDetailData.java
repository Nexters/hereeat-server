package com.yogieat.external.kakao.result;

import com.yogieat.category.domain.value.LargeCategory;
import com.yogieat.gathering.domain.value.TimeSlot;
import java.time.LocalDate;
import java.util.List;

/**
 * Kakao Place API panel3 endpoint response data
 * Contains detailed information about a place including rating, photos, menus, and AI summary
 */
public record KakaoPlaceDetailData(
        String confirmId,
        String placeName,
        String address,
        Double latitude,
        Double longitude,
        Double rating,
        String mainPhotoUrl,
        List<String> photoUrls,
        String representativeReview,
        // 추천 근거 데이터 (신규 필드)
        Integer reviewCount,
        Integer blogReviewCount,
        String representMenu,
        Integer representMenuPrice,
        String priceLevel,
        String aiMateSummaryTitle,
        List<String> aiMateSummaryContents,
        // 추천 시간대 (신규 필드)
        TimeSlot timeSlot,
        // 카카오 API 원본 카테고리 텍스트
        String apiCategoryName2,
        String apiCategoryName3,
        // 카카오 API에서 추출한 카테고리 정보
        LargeCategory apiLargeCategory,
        String apiMediumCategory,
        // 휴무일
        List<LocalDate> offDays
) {
    /**
     * Create a minimal detail data when panel3 call fails
     */
    public static KakaoPlaceDetailData empty(String confirmId) {
        return new KakaoPlaceDetailData(
                confirmId,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                List.of()
        );
    }
}
