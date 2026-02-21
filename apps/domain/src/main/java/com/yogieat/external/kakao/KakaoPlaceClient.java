package com.yogieat.external.kakao;

import com.yogieat.external.kakao.result.KaKaoPlaceDocumentResult;
import java.util.List;
import java.util.Optional;

public interface KakaoPlaceClient {

    List<KaKaoPlaceDocumentResult> searchPlaces(String placeName, String region, int size);

    default Optional<KaKaoPlaceDocumentResult> searchPlace(String placeName, String region, int size) {
        List<KaKaoPlaceDocumentResult> results = searchPlaces(placeName, region, size);
        if (results.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(results.getFirst());
    }

    default Optional<KaKaoPlaceDocumentResult> searchPlace(String placeName, String region) {
        return searchPlace(placeName, region, 1);
    }
}
