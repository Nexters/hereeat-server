package com.yogieat.global.config.swagger;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "swagger")
public record SwaggerProperties (
    String domain,
    String user,
    String password
) {}
