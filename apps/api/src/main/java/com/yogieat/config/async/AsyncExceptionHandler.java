package com.yogieat.config.async;

import com.yogieat.common.error.CustomException;
import java.lang.reflect.Method;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.http.HttpStatus;

public class AsyncExceptionHandler implements AsyncUncaughtExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AsyncExceptionHandler.class);

    @Override
    public void handleUncaughtException(
            @NonNull Throwable ex,
            @NonNull Method method,
            Object @NonNull ... params
    ) {
        if (ex instanceof CustomException customException) {
            HttpStatus status = customException.getErrorCode().getStatus();
            String errorMessage =
                    String.format(
                            "CustomException in async method '%s': %s",
                            method.getName(), ex.getMessage());

            if (status.is5xxServerError()) {
                log.error(errorMessage, ex);
            } else if (status.is4xxClientError()) {
                log.warn(errorMessage, ex);
            } else {
                log.info(errorMessage, ex);
            }
        } else {
            log.error(
                    "Exception in async method '{}': {}",
                    method.getName(),
                    ex.getMessage(),
                    ex);
        }
    }
}
