package com.yogieat.global.config.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kakao.api")
public record KakaoProperties(
    String apiKey,
    String baseUrl
) {}
