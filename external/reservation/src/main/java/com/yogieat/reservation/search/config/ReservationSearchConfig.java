package com.yogieat.reservation.search.config;

import java.lang.reflect.Method;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(ReservationSearchProperties.class)
public class ReservationSearchConfig {

    @Bean
    public RestClient reservationSearchRestClient(ReservationSearchProperties properties) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(3))
                        .build()
        );
        applyTimeout(requestFactory, "setConnectTimeout", Duration.ofSeconds(3));
        applyTimeout(requestFactory, "setReadTimeout", properties.resolvedReadTimeout());

        RestClient.Builder builder = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(properties.resolvedBaseUrl())
                .defaultHeader("User-Agent", properties.resolvedUserAgent());

        if (properties.isNaverOpenApiProvider() && properties.hasNaverCredentials()) {
            builder.defaultHeader("X-Naver-Client-Id", properties.naverClientId());
            builder.defaultHeader("X-Naver-Client-Secret", properties.naverClientSecret());
        }

        return builder.build();
    }

    private void applyTimeout(JdkClientHttpRequestFactory requestFactory, String methodName, Duration timeout) {
        try {
            Method method = requestFactory.getClass().getMethod(methodName, Duration.class);
            method.invoke(requestFactory, timeout);
        } catch (NoSuchMethodException ignored) {
            // fallback for runtime compatibility
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to configure reservation search timeout", exception);
        }
    }
}
