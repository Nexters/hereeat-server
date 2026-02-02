package com.yogieat.support.swagger;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "swagger")
public record SwaggerProperties (
    String domain,
    String user,
    String password
) {}
