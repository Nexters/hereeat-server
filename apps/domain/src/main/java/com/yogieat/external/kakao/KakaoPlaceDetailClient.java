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

    Optional<KakaoPlaceDetailData> fetchPlaceDetail(String placeId);
}
