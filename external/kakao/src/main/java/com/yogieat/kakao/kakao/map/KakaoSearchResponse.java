package com.yogieat.kakao.kakao.map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoSearchResponse(
        KakaoSearchMeta meta,
        List<KaKaoPlaceDocument> documents
) {
}
