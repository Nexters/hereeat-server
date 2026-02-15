package com.yogieat.kakao.kakao.map;

import com.fasterxml.jackson.databind.JsonNode;
import com.yogieat.external.kakao.KakaoPlaceDetailClient;
import com.yogieat.external.kakao.result.KakaoPlaceDetailData;
import com.yogieat.external.kakao.result.KakaoPlaceDetailFetchResult;
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
    public KakaoPlaceDetailFetchResult fetchPlaceDetailResult(String placeId) {
        if (placeId == null || placeId.isBlank()) {
            log.warn("Cannot fetch place detail: placeId is null or blank");
            return KakaoPlaceDetailFetchResult.unavailable();
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
                return KakaoPlaceDetailFetchResult.unavailable();
            }

            KakaoPlaceDetailData detailData = parser.parse(panel, placeId);
            if (detailData.confirmId() == null) {
                log.warn("Failed to parse valid detail data for placeId: {}", placeId);
                return KakaoPlaceDetailFetchResult.unavailable();
            }

            log.debug("Successfully fetched place detail: placeId={}, rating={}, photos={}",
                    placeId, detailData.rating(), detailData.photoUrls().size());
            return KakaoPlaceDetailFetchResult.success(detailData);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404 && e.getResponseBodyAsString() != null
                    && e.getResponseBodyAsString().contains("\"code\":\"NONE\"")) {
                log.warn("panel3 place not found: placeId={}, status={}, body={}",
                        placeId, e.getStatusCode().value(), e.getResponseBodyAsString());
                return KakaoPlaceDetailFetchResult.notFound();
            }

            log.error("panel3 API call failed: placeId={}, status={}, body={}",
                    placeId, e.getStatusCode().value(), e.getResponseBodyAsString());
            return KakaoPlaceDetailFetchResult.unavailable();
        } catch (Exception e) {
            log.error("Unexpected error fetching place detail for placeId: {}", placeId, e);
            return KakaoPlaceDetailFetchResult.unavailable();
        }
    }

    @Override
    public Optional<KakaoPlaceDetailData> fetchPlaceDetail(String placeId) {
        return Optional.ofNullable(fetchPlaceDetailResult(placeId).detail());
    }
}
