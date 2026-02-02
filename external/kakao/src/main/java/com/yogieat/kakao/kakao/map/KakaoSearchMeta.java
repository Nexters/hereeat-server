package com.yogieat.kakao.kakao.map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoSearchMeta(
        @JsonProperty("same_name")
        SameName sameName,
        @JsonProperty("pageable_count")
        int pageableCount,
        @JsonProperty("total_count")
        int totalCount,
        @JsonProperty("is_end")
        boolean isEnd
) {
}
