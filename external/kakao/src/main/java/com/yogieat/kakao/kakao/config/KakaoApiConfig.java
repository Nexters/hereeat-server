package com.yogieat.kakao.kakao.config;

import java.net.http.HttpClient;
import java.security.GeneralSecurityException;
import java.time.Duration;
import javax.net.ssl.SSLContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Configuration for Kakao API clients
 * Centralizes RestClient configuration following DRY principle
 */
@Configuration
@ConditionalOnProperty(name = "kakao.api.client.enabled", havingValue = "true", matchIfMissing = false)
public class KakaoApiConfig {

    private static final String PANEL3_BASE_URL = "https://place-api.map.kakao.com";
    private static final String REFERER = "https://place.map.kakao.com/";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                    + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36";

    /**
     * RestClient for Kakao Place panel3 API
     * Used to fetch detailed place information (rating, photos, etc.)
     */
    @Bean
    public RestClient kakaoPlaceDetailRestClient() {
        return RestClient.builder()
            .requestFactory(new JdkClientHttpRequestFactory(httpClient()))
                .baseUrl(PANEL3_BASE_URL)
                .defaultHeader("Pf", "web")
                .defaultHeader(HttpHeaders.REFERER, REFERER)
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .build();
    }

    private HttpClient httpClient() {
        SSLContext sslContext = createSslContext();
        return HttpClient.newBuilder()
            .sslContext(sslContext)
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    }

    private SSLContext createSslContext() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, null, null);
            return sslContext;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to initialize TLS SSLContext for Kakao detail client", e);
        }
    }
}
