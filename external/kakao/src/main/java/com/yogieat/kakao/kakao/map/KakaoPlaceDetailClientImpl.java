package com.yogieat.kakao.kakao.map;

import com.fasterxml.jackson.databind.JsonNode;
import com.yogieat.external.kakao.KakaoPlaceDetailClient;
import com.yogieat.external.kakao.result.KakaoPlaceDetailData;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
@Slf4j
public class KakaoPlaceDetailClientImpl implements KakaoPlaceDetailClient {

    private final KakaoPlaceDetailParser parser;
    private final RestClient restClient;

    public KakaoPlaceDetailClientImpl(
            KakaoPlaceDetailParser parser,
            @Qualifier("kakaoPlaceDetailRestClient") RestClient restClient
    ) {
        this.parser = parser;
        this.restClient = restClient;
    }

    @Override
    public Optional<KakaoPlaceDetailData> fetchPlaceDetail(String placeId) {
        if (placeId == null || placeId.isBlank()) {
            log.warn("Cannot fetch place detail: placeId is null or blank");
            return Optional.empty();
        }

        try {
            log.debug("Fetching place detail from panel3 for placeId: {}", placeId);

            JsonNode panel = restClient.get()
                    .uri("/places/panel3/{placeId}", placeId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(JsonNode.class);

            if (panel == null) {
                log.warn("panel3 API returned null response for placeId: {}", placeId);
                return Optional.empty();
            }

            KakaoPlaceDetailData detailData = parser.parse(panel, placeId);
            if (detailData.confirmId() == null) {
                log.warn("Failed to parse valid detail data for placeId: {}", placeId);
                return Optional.empty();
            }

            log.debug("Successfully fetched place detail: placeId={}, rating={}, photos={}",
                    placeId, detailData.rating(), detailData.photoUrls().size());
            return Optional.of(detailData);
        } catch (RestClientResponseException e) {
            log.error("panel3 API call failed: placeId={}, status={}, body={}",
                    placeId, e.getStatusCode().value(), e.getResponseBodyAsString());
            return Optional.empty();
        } catch (Exception e) {
            log.error("Unexpected error fetching place detail for placeId: {}", placeId, e);
            return Optional.empty();
        }
    }
}
