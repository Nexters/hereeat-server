package com.yogieat.reservation.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.yogieat.reservation.search.config.ReservationSearchProperties;
import com.yogieat.restaurant.reservation.domain.value.ReservationProvider;
import java.io.ByteArrayInputStream;
import java.io.IOException;
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

class DuckDuckGoReservationSearchClientTest {

    @Test
    @DisplayName("검색이 403으로 차단되면 cooldown 동안 추가 요청을 보내지 않는다")
    void searchBestCandidate_ShouldStopRequestsDuringBlockedCooldown() {
        CountingStatusRequestFactory requestFactory = new CountingStatusRequestFactory(HttpStatus.FORBIDDEN);
        DuckDuckGoReservationSearchClient client = new DuckDuckGoReservationSearchClient(
                properties(),
                RestClient.builder()
                        .baseUrl("https://html.duckduckgo.com")
                        .requestFactory(requestFactory)
                        .build()
        );

        assertThat(client.searchBestCandidate(
                ReservationProvider.NAVER_BOOKING,
                "이치류 홍대본점",
                "서울 마포구"
        )).isEqualTo(Optional.empty());
        assertThat(client.searchBestCandidate(
                ReservationProvider.CATCHTABLE,
                "이치류 홍대본점",
                "서울 마포구"
        )).isEqualTo(Optional.empty());

        assertThat(requestFactory.requestCount()).isEqualTo(1);
    }

    private ReservationSearchProperties properties() {
        return new ReservationSearchProperties(
                true,
                "duckduckgo",
                "https://html.duckduckgo.com",
                "test-agent",
                0.6d,
                5,
                2,
                60L,
                0L,
                "https://openapi.naver.com",
                null,
                null,
                10,
                "http://yogieat-reservation-browser-worker:8090",
                0L
        );
    }

    private static final class CountingStatusRequestFactory implements ClientHttpRequestFactory {

        private final HttpStatus status;
        private final AtomicInteger requestCount = new AtomicInteger();

        private CountingStatusRequestFactory(HttpStatus status) {
            this.status = status;
        }

        @Override
        public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) {
            requestCount.incrementAndGet();
            return new StatusClientHttpRequest(uri, httpMethod, status);
        }

        private int requestCount() {
            return requestCount.get();
        }
    }

    private static final class StatusClientHttpRequest implements ClientHttpRequest {

        private final URI uri;
        private final HttpMethod method;
        private final HttpStatus status;
        private final HttpHeaders headers = new HttpHeaders();
        private final Map<String, Object> attributes = new HashMap<>();

        private StatusClientHttpRequest(URI uri, HttpMethod method, HttpStatus status) {
            this.uri = uri;
            this.method = method;
            this.status = status;
        }

        @Override
        public ClientHttpResponse execute() {
            return new StatusClientHttpResponse(status);
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

    private static final class StatusClientHttpResponse implements ClientHttpResponse {

        private final HttpStatus status;

        private StatusClientHttpResponse(HttpStatus status) {
            this.status = status;
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
        public ByteArrayInputStream getBody() throws IOException {
            return new ByteArrayInputStream("blocked".getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public HttpHeaders getHeaders() {
            return new HttpHeaders();
        }
    }
}
