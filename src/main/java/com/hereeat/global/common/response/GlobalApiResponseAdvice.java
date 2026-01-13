package com.hereeat.global.common.response;

import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice(basePackages = "com.hereeat")
public class GlobalApiResponseAdvice implements ResponseBodyAdvice<Object> {
    
    @Override
    public boolean supports(@NonNull MethodParameter returnType, @NonNull Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            @NonNull MethodParameter returnType,
            @NonNull MediaType selectedContentType,
            @NonNull Class<? extends HttpMessageConverter<?>> selectedConverterType,
            @NonNull ServerHttpRequest request,
            @NonNull ServerHttpResponse response) {
        
        // 이미 GlobalResponse로 래핑된 경우 그대로 반환 (이중 래핑 방지)
        if (body instanceof GlobalApiResponse) {
            return body;
        }
        
        // Actuator endpoint는 GlobalResponse로 래핑하지 않음
        String path = request.getURI().getPath();
        if (path.startsWith("/actuator")) {
            return body;
        }
        
        // String 타입은 GlobalResponse로 래핑하지 않음 (JSON 변환 오류 방지)
        if (body instanceof String) {
            return body;
        }
        
        // HTTP 상태 코드 확인
        HttpServletResponse servletResponse =
                ((ServletServerHttpResponse) response).getServletResponse();
        int status = servletResponse.getStatus();
        HttpStatus httpStatus = HttpStatus.resolve(status);
        
        // 비표준 HTTP 상태 코드이거나 2xx 성공 응답이 아닌 경우 그대로 반환
        if (httpStatus == null || !httpStatus.is2xxSuccessful()) {
            return body;
        }
        
        // 2xx 성공 응답을 GlobalResponse로 래핑
        return GlobalApiResponse.success(status, body);
    }
}
