package com.yogieat.external.kakao;

import com.yogieat.external.kakao.result.KaKaoPlaceDocumentResult;
import com.yogieat.external.kakao.result.KakaoRestaurantData;

public interface KakaoPlaceMapper {
    KakaoRestaurantData toDomainData(KaKaoPlaceDocumentResult document);
}
