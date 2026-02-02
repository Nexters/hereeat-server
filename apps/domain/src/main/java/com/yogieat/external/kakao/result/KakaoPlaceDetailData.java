package com.yogieat.external.kakao.result;

import java.util.List;

/**
 * Kakao Place API panel3 endpoint response data
 * Contains detailed information about a place including rating, photos, and menus
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
        String representativeReview
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
                null
        );
    }
}
