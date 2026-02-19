package com.yogieat.controller.advice;

import com.yogieat.common.error.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ErrorHttpStatusMapper {

    public HttpStatus toHttpStatus(ErrorCode errorCode) {
        return switch (errorCode) {
            case METHOD_ARGUMENT_TYPE_MISMATCH -> HttpStatus.BAD_REQUEST;
            case INVALID_LOCATION_NAME -> HttpStatus.BAD_REQUEST;
            case METHOD_NOT_ALLOWED -> HttpStatus.METHOD_NOT_ALLOWED;
            case ADMIN_NOT_FOUND,
                 ADMIN_INVALID_PASSWORD,
                 ADMIN_UNAUTHORIZED,
                 ADMIN_TOKEN_EXPIRED,
                 ADMIN_TOKEN_INVALID -> HttpStatus.UNAUTHORIZED;
            case ADMIN_FORBIDDEN -> HttpStatus.FORBIDDEN;
            case RESTAURANT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
