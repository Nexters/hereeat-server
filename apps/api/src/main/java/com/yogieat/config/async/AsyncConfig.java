package com.yogieat.config.async;

import com.yogieat.controller.advice.ErrorHttpStatusMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 비동기 예외 처리 설정.
 *
 * <p>{@code spring.threads.virtual.enabled=true}에 의해 Virtual Thread가
 * 자동 적용되므로, Executor 설정 없이 예외 핸들러만 등록합니다.</p>
 */
@Configuration
@EnableAsync
@RequiredArgsConstructor
public class AsyncConfig implements AsyncConfigurer {
    private final ErrorHttpStatusMapper errorHttpStatusMapper;

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new AsyncExceptionHandler(errorHttpStatusMapper);
    }
}
