package com.yogieat.external.kakao;

import com.yogieat.external.kakao.result.KakaoPlaceDetailData;
import java.util.Optional;

public interface KakaoPlaceDetailClient {
    Optional<KakaoPlaceDetailData> fetchPlaceDetail(String placeId);
}
