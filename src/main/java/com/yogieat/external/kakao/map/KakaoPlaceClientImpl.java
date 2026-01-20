package com.yogieat.external.kakao.map;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
@Slf4j
public class KakaoPlaceClientImpl implements KakaoPlaceClient {

    private final RestClient kakaoRestClient;

    @Override
    public Optional<KaKaoPlaceDocument> searchPlace(String placeName, String region) {
        try {
            log.debug("Searching Kakao Place API for: {} in {}", placeName, region);

            KakaoSearchResponse response = kakaoRestClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/v2/local/search/keyword.json")
                    .queryParam("query", placeName + " " + region)
                    .queryParam("size", 1)
                    .build())
                .retrieve()
                .body(KakaoSearchResponse.class);

            if (response != null && !response.documents().isEmpty()) {
                KaKaoPlaceDocument document = response.documents().get(0);
                log.debug("Found place: {}", document.placeName());
                return Optional.of(document);
            }

            log.warn("No place found for: {} in {}", placeName, region);
            return Optional.empty();

        } catch (Exception e) {
            log.warn("Kakao API search failed for place: {} in region: {}", placeName, region, e);
            return Optional.empty();
        }
    }
}
