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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;

class NaverOpenApiReservationSearchClientTest {

    @Test
    @DisplayName("네이버 웹문서 검색 결과에서 예약 URL 후보를 추출한다")
    void searchBestCandidate_ShouldExtractReservationCandidateFromNaverWebSearch() {
        String responseBody = """
                {
                  "items": [
                    {
                      "title": "<b>이치류 홍대본점</b> - 네이버 예약",
                      "link": "https://booking.naver.com/booking/6/bizes/591723",
                      "description": "서울 마포구 이치류 홍대본점 예약"
                    }
                  ]
                }
                """;
        CountingResponseRequestFactory requestFactory = new CountingResponseRequestFactory(HttpStatus.OK, responseBody);
        NaverOpenApiReservationSearchClient client = new NaverOpenApiReservationSearchClient(
                properties(),
                RestClient.builder()
                        .baseUrl("https://openapi.naver.com")
                        .requestFactory(requestFactory)
                        .build()
        );

        Optional<ReservationSearchCandidate> candidate = client.searchBestCandidate(
                ReservationProvider.NAVER_BOOKING,
                "이치류 홍대본점",
                "서울 마포구"
        );

        assertThat(candidate).isPresent();
        assertThat(candidate.get().reservationUrl()).isEqualTo("https://booking.naver.com/booking/6/bizes/591723");
        assertThat(candidate.get().providerPlaceKey()).isEqualTo("591723");
        assertThat(requestFactory.requestCount()).isEqualTo(1);
    }

    private ReservationSearchProperties properties() {
        return new ReservationSearchProperties(
                true,
                "naver-open-api",
                "https://html.duckduckgo.com",
                "test-agent",
                0.6d,
                5,
                1,
                60L,
                0L,
                "https://openapi.naver.com",
                "client-id",
                "client-secret",
                10,
                "http://yogieat-reservation-browser-worker:8090",
                0L
        );
    }

    private static final class CountingResponseRequestFactory implements ClientHttpRequestFactory {

        private final HttpStatus status;
        private final String responseBody;
        private final AtomicInteger requestCount = new AtomicInteger();

        private CountingResponseRequestFactory(HttpStatus status, String responseBody) {
            this.status = status;
            this.responseBody = responseBody;
        }

        @Override
        public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) {
            requestCount.incrementAndGet();
            return new ResponseClientHttpRequest(uri, httpMethod, status, responseBody);
        }

        private int requestCount() {
            return requestCount.get();
        }
    }

    private static final class ResponseClientHttpRequest implements ClientHttpRequest {

        private final URI uri;
        private final HttpMethod method;
        private final HttpStatus status;
        private final String responseBody;
        private final HttpHeaders headers = new HttpHeaders();
        private final Map<String, Object> attributes = new HashMap<>();

        private ResponseClientHttpRequest(URI uri, HttpMethod method, HttpStatus status, String responseBody) {
            this.uri = uri;
            this.method = method;
            this.status = status;
            this.responseBody = responseBody;
        }

        @Override
        public ClientHttpResponse execute() {
            return new ResponseClientHttpResponse(status, responseBody);
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

    private static final class ResponseClientHttpResponse implements ClientHttpResponse {

        private final HttpStatus status;
        private final String responseBody;

        private ResponseClientHttpResponse(HttpStatus status, String responseBody) {
            this.status = status;
            this.responseBody = responseBody;
        }

        @Override
        public HttpStatus getStatusCode() {
            return status;
        }

        @Override
        public String getStatusText() {
            return status.getReasonPhrase();
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
