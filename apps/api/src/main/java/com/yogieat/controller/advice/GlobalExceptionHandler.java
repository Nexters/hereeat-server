package com.yogieat.controller.advice;

import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import jakarta.validation.ConstraintViolationException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private final ErrorHttpStatusMapper errorHttpStatusMapper;

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            Object body,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode statusCode,
            @NonNull WebRequest request) {
        ErrorResponse errorResponse =
                ErrorResponse.of(ex.getClass().getSimpleName(), ex.getMessage());
        return super.handleExceptionInternal(ex, errorResponse, headers, statusCode, request);
    }

    /**
     * javax.validation.Valid or @Validated 으로 binding error 발생시 발생한다. HttpMessageConverter 에서 등록한
     * HttpMessageConverter binding 못할경우 발생 주로 @RequestBody, @RequestPart 어노테이션에서 발생
     */
    @SneakyThrows
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            @NonNull MethodArgumentNotValidException e,
            @NonNull HttpHeaders headers,
            HttpStatusCode status,
            @NonNull WebRequest request) {
        log.error("MethodArgumentNotValidException : {}", e.getMessage(), e);
        String errorMessage = e.getBindingResult().getAllErrors().getFirst().getDefaultMessage();
        final ErrorResponse errorResponse =
                ErrorResponse.of(e.getClass().getSimpleName(), errorMessage);
        GlobalApiResponse response = GlobalApiResponse.fail(status.value(), errorResponse);
        return ResponseEntity.status(status).body(response);
    }

    /** Request Param Validation 예외 처리 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<GlobalApiResponse> handleConstraintViolationException(
            ConstraintViolationException e) {
        log.error("ConstraintViolationException : {}", e.getMessage(), e);

        Map<String, Object> bindingErrors = new HashMap<>();
        e.getConstraintViolations()
                .forEach(
                        constraintViolation -> {
                            List<String> propertyPath =
                                    List.of(
                                            constraintViolation
                                                    .getPropertyPath()
                                                    .toString()
                                                    .split("\\."));
                            String path =
                                    propertyPath.stream()
                                            .skip(propertyPath.size() - 1L)
                                            .findFirst()
                                            .orElse(null);
                            bindingErrors.put(path, constraintViolation.getMessage());
                        });

        final ErrorResponse errorResponse =
                ErrorResponse.of(e.getClass().getSimpleName(), bindingErrors.toString());
        final GlobalApiResponse response =
                GlobalApiResponse.fail(HttpStatus.BAD_REQUEST.value(), errorResponse);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /** PathVariable, RequestParam, RequestHeader, RequestBody 에서 타입이 일치하지 않을 경우 발생 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    protected ResponseEntity<GlobalApiResponse> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException e) {
        log.error("MethodArgumentTypeMismatchException : {}", e.getMessage(), e);
        final ErrorCode errorCode = ErrorCode.METHOD_ARGUMENT_TYPE_MISMATCH;
        final HttpStatus status = errorHttpStatusMapper.toHttpStatus(errorCode);
        final ErrorResponse errorResponse =
                ErrorResponse.of(e.getClass().getSimpleName(), errorCode.getMessage());
        final GlobalApiResponse response =
                GlobalApiResponse.fail(status.value(), errorResponse);
        return ResponseEntity.status(status).body(response);
    }

    /** 지원하지 않은 HTTP method 호출 할 경우 발생 */
    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException e,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        log.error("HttpRequestMethodNotSupportedException : {}", e.getMessage(), e);
        final ErrorCode errorCode = ErrorCode.METHOD_NOT_ALLOWED;
        final HttpStatus statusMapped = errorHttpStatusMapper.toHttpStatus(errorCode);
        final ErrorResponse errorResponse =
                ErrorResponse.of(e.getClass().getSimpleName(), errorCode.getMessage());
        final GlobalApiResponse response =
                GlobalApiResponse.fail(statusMapped.value(), errorResponse);
        return ResponseEntity.status(statusMapped).body(response);
    }

     @ExceptionHandler(CustomException.class)
     public ResponseEntity<GlobalApiResponse> handleCustomException(CustomException e) {
         log.error("CustomException : {}", e.getMessage(), e);
         final ErrorCode errorCode = e.getErrorCode();
         final HttpStatus status = errorHttpStatusMapper.toHttpStatus(errorCode);
         final ErrorResponse errorResponse =
                 ErrorResponse.of(errorCode.getCode(), errorCode.getMessage());
         final GlobalApiResponse response =
                 GlobalApiResponse.fail(status.value(), errorResponse);
         return ResponseEntity.status(status).body(response);
     }

    /**
     * HTTP 메시지를 읽을 수 없을 때 발생하는 예외 처리
     * JSON 역직렬화 중 발생한 CustomException을 처리
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            @NonNull HttpMessageNotReadableException e,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        log.error("HttpMessageNotReadableException : {}", e.getMessage(), e);

        // 원인이 CustomException인 경우 처리
        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause instanceof CustomException customException) {
                final ErrorCode errorCode = customException.getErrorCode();
                final HttpStatus statusMapped = errorHttpStatusMapper.toHttpStatus(errorCode);
                final ErrorResponse errorResponse =
                        ErrorResponse.of(errorCode.getCode(), errorCode.getMessage());
                final GlobalApiResponse response =
                        GlobalApiResponse.fail(statusMapped.value(), errorResponse);
                return ResponseEntity.status(statusMapped).body(response);
            }
            cause = cause.getCause();
        }

        // CustomException이 아닌 경우 기본 처리
        final ErrorResponse errorResponse =
                ErrorResponse.of(e.getClass().getSimpleName(), "잘못된 요청 형식입니다");
        final GlobalApiResponse response =
                GlobalApiResponse.fail(HttpStatus.BAD_REQUEST.value(), errorResponse);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /** 500번대 에러 처리 */
    @ExceptionHandler(Exception.class)
    protected ResponseEntity<GlobalApiResponse> handleException(Exception e) {
        log.error("Internal Server Error : {}", e.getMessage(), e);
        final ErrorCode internalServerError = ErrorCode.INTERNAL_SERVER_ERROR;
        final HttpStatus status = errorHttpStatusMapper.toHttpStatus(internalServerError);
        final ErrorResponse errorResponse =
                ErrorResponse.of(e.getClass().getSimpleName(), internalServerError.getMessage());
        final GlobalApiResponse response =
                GlobalApiResponse.fail(status.value(), errorResponse);
        return ResponseEntity.status(status).body(response);
    }
}
