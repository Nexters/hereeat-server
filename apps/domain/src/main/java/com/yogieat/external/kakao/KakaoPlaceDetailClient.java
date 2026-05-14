package com.yogieat.external.kakao;

import com.yogieat.external.kakao.result.KakaoPlaceDetailData;
import com.yogieat.external.kakao.result.KakaoPlaceDetailFetchResult;
import java.util.Optional;

public interface KakaoPlaceDetailClient {

    default KakaoPlaceDetailFetchResult fetchPlaceDetailResult(String placeId) {
        return fetchPlaceDetail(placeId)
                .map(KakaoPlaceDetailFetchResult::success)
                .orElseGet(KakaoPlaceDetailFetchResult::unavailable);
    }

    default void prefetchPlaceDetailResult(String placeId) {
    }

    Optional<KakaoPlaceDetailData> fetchPlaceDetail(String placeId);
}
