package com.yogieat.kakao.kakao.map;

import com.yogieat.external.kakao.KakaoPlaceClient;
import com.yogieat.external.kakao.result.KaKaoPlaceDocumentResult;
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
    public Optional<KaKaoPlaceDocumentResult> searchPlace(String placeName, String region) {
        try {
            log.debug("Searching Kakao Place API for: {} in {}", placeName, region);
            String searchPlaceName = placeName.split(" ")[0];
            KakaoSearchResponse response = kakaoRestClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/v2/local/search/keyword.json")
                    .queryParam("query", searchPlaceName + " " + region)
                    .queryParam("size", 1)
                    .build())
                .retrieve()
                .body(KakaoSearchResponse.class);

            if (response != null && !response.documents().isEmpty()) {
                KaKaoPlaceDocument document = response.documents().getFirst();
                log.debug("Found place: {}", document.placeName());
                return Optional.of(document.toResult());
            }

            log.warn("No place found for: {} in {}", searchPlaceName, region);
            return Optional.empty();

        } catch (Exception e) {
            log.warn("Kakao API search failed for place: {} in region: {}", placeName, region, e);
            return Optional.empty();
        }
    }
}
