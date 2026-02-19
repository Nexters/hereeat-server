package com.yogieat.controller.advice;

import static org.assertj.core.api.Assertions.assertThat;

import com.yogieat.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ErrorHttpStatusMapperTest {

    private final ErrorHttpStatusMapper mapper = new ErrorHttpStatusMapper();

    @Test
    @DisplayName("모든 에러 코드는 null 아닌 HTTP 상태로 매핑된다")
    void shouldMapAllErrorCodes() {
        for (ErrorCode errorCode : ErrorCode.values()) {
            assertThat(mapper.toHttpStatus(errorCode)).isNotNull();
        }
    }

    @Test
    @DisplayName("Admin 에러코드를 정책대로 상태코드에 매핑한다")
    void shouldMapAdminErrorCodes() {
        assertThat(mapper.toHttpStatus(ErrorCode.ADMIN_NOT_FOUND)).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(mapper.toHttpStatus(ErrorCode.ADMIN_INVALID_PASSWORD)).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(mapper.toHttpStatus(ErrorCode.ADMIN_UNAUTHORIZED)).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(mapper.toHttpStatus(ErrorCode.ADMIN_TOKEN_EXPIRED)).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(mapper.toHttpStatus(ErrorCode.ADMIN_TOKEN_INVALID)).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(mapper.toHttpStatus(ErrorCode.ADMIN_FORBIDDEN)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(mapper.toHttpStatus(ErrorCode.INVALID_LOCATION_NAME)).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(mapper.toHttpStatus(ErrorCode.RESTAURANT_NOT_FOUND)).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(mapper.toHttpStatus(ErrorCode.GATHERING_NOT_FOUND)).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
