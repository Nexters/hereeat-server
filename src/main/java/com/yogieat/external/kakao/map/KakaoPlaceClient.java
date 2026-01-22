package com.yogieat.external.kakao.map;

import java.util.Optional;

public interface KakaoPlaceClient {
    Optional<KaKaoPlaceDocument> searchPlace(String placeName, String region);
}
