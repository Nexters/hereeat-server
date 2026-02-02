package com.yogieat.external.kakao;

import com.yogieat.external.kakao.result.KaKaoPlaceDocumentResult;
import java.util.Optional;

public interface KakaoPlaceClient {
    Optional<KaKaoPlaceDocumentResult> searchPlace(String placeName, String region);
}
