package com.yogieat.kakao.kakao.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class KakaoClientConfig {

    @Bean
    public RestClient kakaoRestClient(KakaoProperties properties) {
        return RestClient.builder()
            .baseUrl(properties.baseUrl())
            .defaultHeader("Authorization", "KakaoAK " + properties.apiKey())
            .defaultHeader("Content-Type", "application/json")
            .build();
    }
}
