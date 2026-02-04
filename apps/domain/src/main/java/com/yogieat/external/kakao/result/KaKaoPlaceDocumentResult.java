package com.yogieat.external.kakao.result;

public record KaKaoPlaceDocumentResult(
        String id,
        String placeName,
        String categoryName,
        String categoryGroupCode,
        String categoryGroupName,
        String phone,
        String addressName,
        String roadAddressName,
        String x,
        String y,
        String placeUrl,
        String distance
) {
}
