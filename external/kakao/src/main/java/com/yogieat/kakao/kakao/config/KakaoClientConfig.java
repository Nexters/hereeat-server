package com.yogieat.kakao.kakao.config;

import java.lang.reflect.Method;
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
        JdkClientHttpRequestFactory requestFactory = requestFactory();
        return RestClient.builder()
            .requestFactory(requestFactory)
            .baseUrl(properties.baseUrl())
            .defaultHeader("Authorization", "KakaoAK " + properties.apiKey())
            .defaultHeader("Content-Type", "application/json")
            .build();
    }

    private JdkClientHttpRequestFactory requestFactory() {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient());
        applyRequestTimeout(requestFactory, Duration.ofSeconds(2), Duration.ofSeconds(8));
        return requestFactory;
    }

    private void applyRequestTimeout(JdkClientHttpRequestFactory requestFactory, Duration connectTimeout,
            Duration readTimeout) {
        setTimeoutIfSupported(requestFactory, "setConnectTimeout", connectTimeout);
        setTimeoutIfSupported(requestFactory, "setReadTimeout", readTimeout);
    }

    private void setTimeoutIfSupported(JdkClientHttpRequestFactory requestFactory, String methodName, Duration timeout) {
        try {
            Method method = requestFactory.getClass().getMethod(methodName, Duration.class);
            method.invoke(requestFactory, timeout);
            return;
        } catch (NoSuchMethodException ignored) {
            // Older Spring versions may not expose Duration-based timeout setters.
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to set Kakao client request timeout", exception);
        }

        try {
            Method method = requestFactory.getClass().getMethod(methodName, long.class);
            method.invoke(requestFactory, timeout.toMillis());
            return;
        } catch (NoSuchMethodException ignored) {
            // Fallback not supported
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to set Kakao client request timeout", exception);
        }

        try {
            Method method = requestFactory.getClass().getMethod(methodName, int.class);
            method.invoke(requestFactory, Math.toIntExact(timeout.toMillis()));
        } catch (NoSuchMethodException ignored) {
            // Timeout setter is not available in this runtime. Fallback to defaults.
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to set Kakao client request timeout", exception);
        } catch (ArithmeticException ignored) {
            // Fallback not possible when timeout value cannot fit into int.
        }
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
