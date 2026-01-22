package com.yogieat.external.kakao.map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SameName(
        List<String> region,
        String keyword,
        @JsonProperty("selected_region")
        String selectedRegion
) {
}
