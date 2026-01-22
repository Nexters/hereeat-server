package com.yogieat.external.kakao.map;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * 카카오 장소 API panel3 엔드포인트 클라이언트
 * 평점 및 사진을 포함한 상세 장소 정보를 가져옴
 *
 * 책임:
 * - panel3 API와의 HTTP 통신
 * - 에러 처리 및 로깅
 * - KakaoPlaceDetailParser로 파싱 위임 (단일 책임 원칙)
 */
@Component
@Slf4j
public class KakaoPlaceDetailClient {

    private final KakaoPlaceDetailParser parser;
    private final RestClient restClient;

    /**
     * 의존성 주입을 사용한 생성자
     * @Qualifier는 주입할 RestClient 빈을 지정하기 위해 필요
     */
    public KakaoPlaceDetailClient(
            KakaoPlaceDetailParser parser,
            @Qualifier("kakaoPlaceDetailRestClient") RestClient restClient) {
        this.parser = parser;
        this.restClient = restClient;
    }

    /**
     * panel3 API로부터 상세 장소 정보 조회
     *
     * @param placeId 카카오 장소 ID
     * @return 상세 데이터를 포함한 Optional, API 호출 실패 시 empty
     */
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

            KakaoPlaceDetailData detailData = parser.parser(panel, placeId);

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
