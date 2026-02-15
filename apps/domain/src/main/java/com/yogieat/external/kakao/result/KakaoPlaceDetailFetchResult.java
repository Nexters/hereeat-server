package com.yogieat.external.kakao.result;

public record KakaoPlaceDetailFetchResult(
        KakaoPlaceDetailFetchStatus status,
        KakaoPlaceDetailData detail
) {
    public static KakaoPlaceDetailFetchResult success(KakaoPlaceDetailData detail) {
        return new KakaoPlaceDetailFetchResult(KakaoPlaceDetailFetchStatus.SUCCESS, detail);
    }

    public static KakaoPlaceDetailFetchResult notFound() {
        return new KakaoPlaceDetailFetchResult(KakaoPlaceDetailFetchStatus.NOT_FOUND, null);
    }

    public static KakaoPlaceDetailFetchResult unavailable() {
        return new KakaoPlaceDetailFetchResult(KakaoPlaceDetailFetchStatus.UNAVAILABLE, null);
    }
}
