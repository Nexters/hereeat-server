package com.yogieat.ai.gemini.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.gemini")
public record GeminiProperties(
        String apiKey,
        String model // TODO: 모델에 따라 Rate Limit 제한이 필요
){
}
