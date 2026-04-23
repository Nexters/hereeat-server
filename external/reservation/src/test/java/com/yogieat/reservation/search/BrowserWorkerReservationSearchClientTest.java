package com.yogieat.reservation.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.yogieat.external.reservation.result.ReservationSearchCandidate;
import com.yogieat.reservation.search.config.ReservationSearchProperties;
import com.yogieat.restaurant.reservation.domain.value.ReservationProvider;
import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;

class BrowserWorkerReservationSearchClientTest {

    @Test
    @DisplayName("브라우저 워커 후보 응답을 예약 검색 후보로 변환한다")
    void searchBestCandidate_ShouldConvertBrowserWorkerCandidate() {
        BrowserWorkerReservationSearchClient client = new BrowserWorkerReservationSearchClient(
                properties(),
                RestClient.builder()
                        .baseUrl("http://worker")
                        .requestFactory(new JsonResponseRequestFactory("""
                                {
                                  "reservationUrl": "https://app.catchtable.co.kr/ct/shop/sample",
                                  "providerPlaceKey": "sample",
                                  "matchedName": "샘플식당",
                                  "matchedAddress": "서울 마포구",
                                  "matchScore": 0.85,
                                  "failureReason": null
                                }
                                """))
                        .build()
        );

        Optional<ReservationSearchCandidate> candidate = client.searchBestCandidate(
                ReservationProvider.CATCHTABLE,
                "샘플식당",
                "서울 마포구"
        );

        assertThat(candidate).isPresent();
        assertThat(candidate.get().reservationUrl()).isEqualTo("https://app.catchtable.co.kr/ct/shop/sample");
        assertThat(candidate.get().providerPlaceKey()).isEqualTo("sample");
        assertThat(candidate.get().matchScore()).isEqualTo(0.85d);
        assertThat(candidate.get().searchQuery()).isEqualTo("browser-worker");
    }

    @Test
    @DisplayName("브라우저 워커가 실패 사유만 반환하면 후보 없음으로 처리한다")
    void searchBestCandidate_ShouldReturnEmpty_WhenBrowserWorkerHasNoCandidate() {
        BrowserWorkerReservationSearchClient client = new BrowserWorkerReservationSearchClient(
                properties(),
                RestClient.builder()
                        .baseUrl("http://worker")
                        .requestFactory(new JsonResponseRequestFactory("""
                                {
                                  "reservationUrl": null,
                                  "providerPlaceKey": null,
                                  "matchedName": null,
                                  "matchedAddress": null,
                                  "matchScore": null,
                                  "failureReason": "NO_MATCH"
                                }
                                """))
                        .build()
        );

        Optional<ReservationSearchCandidate> candidate = client.searchBestCandidate(
                ReservationProvider.NAVER_BOOKING,
                "샘플식당",
                "서울 마포구"
        );

        assertThat(candidate).isEmpty();
    }

    private ReservationSearchProperties properties() {
        return new ReservationSearchProperties(
                true,
                "browser-worker",
                "https://html.duckduckgo.com",
                "test-agent",
                0.6d,
                5,
                1,
                60L,
                0L,
                "https://openapi.naver.com",
                null,
                null,
                10,
                "http://worker",
                0L
        );
    }

    private static final class JsonResponseRequestFactory implements ClientHttpRequestFactory {

        private final String responseBody;

        private JsonResponseRequestFactory(String responseBody) {
            this.responseBody = responseBody;
        }

        @Override
        public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) {
            return new JsonClientHttpRequest(uri, httpMethod, responseBody);
        }
    }

    private static final class JsonClientHttpRequest implements ClientHttpRequest {

        private final URI uri;
        private final HttpMethod method;
        private final String responseBody;
        private final HttpHeaders headers = new HttpHeaders();
        private final Map<String, Object> attributes = new HashMap<>();

        private JsonClientHttpRequest(URI uri, HttpMethod method, String responseBody) {
            this.uri = uri;
            this.method = method;
            this.responseBody = responseBody;
        }

        @Override
        public ClientHttpResponse execute() {
            return new JsonClientHttpResponse(responseBody);
        }

        @Override
        public OutputStream getBody() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }

        @Override
        public HttpMethod getMethod() {
            return method;
        }

        @Override
        public URI getURI() {
            return uri;
        }

        @Override
        public Map<String, Object> getAttributes() {
            return attributes;
        }
    }

    private static final class JsonClientHttpResponse implements ClientHttpResponse {

        private final String responseBody;

        private JsonClientHttpResponse(String responseBody) {
            this.responseBody = responseBody;
        }

        @Override
        public HttpStatus getStatusCode() {
            return HttpStatus.OK;
        }

        @Override
        public String getStatusText() {
            return HttpStatus.OK.getReasonPhrase();
        }

        @Override
        public void close() {
        }

        @Override
        public ByteArrayInputStream getBody() {
            return new ByteArrayInputStream(responseBody.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public HttpHeaders getHeaders() {
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_TYPE, "application/json");
            return headers;
        }
    }
}
