package com.yogieat.kakao.kakao.config;

import java.net.http.HttpClient;
import java.security.GeneralSecurityException;
import java.time.Duration;
import javax.net.ssl.SSLContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@ConditionalOnProperty(name = "kakao.api.client.enabled", havingValue = "true", matchIfMissing = false)
public class KakaoClientConfig {

    @Bean
    public RestClient kakaoRestClient(KakaoProperties properties) {
        return RestClient.builder()
            .requestFactory(new JdkClientHttpRequestFactory(httpClient()))
            .baseUrl(properties.baseUrl())
            .defaultHeader("Authorization", "KakaoAK " + properties.apiKey())
            .defaultHeader("Content-Type", "application/json")
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
            throw new IllegalStateException("Failed to initialize TLS SSLContext for Kakao client", e);
        }
    }
}
