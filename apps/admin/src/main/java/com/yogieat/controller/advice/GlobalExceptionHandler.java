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
            @NonNull WebRequest request
    ) {
        ErrorResponse errorResponse =
                ErrorResponse.of(ex.getClass().getSimpleName(), ex.getMessage());
        return super.handleExceptionInternal(ex, errorResponse, headers, statusCode, request);
    }

    @SneakyThrows
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            @NonNull MethodArgumentNotValidException e,
            @NonNull HttpHeaders headers,
            HttpStatusCode status,
            @NonNull WebRequest request
    ) {
        log.error("MethodArgumentNotValidException : {}", e.getMessage(), e);

        String errorMessage = e.getBindingResult().getAllErrors().getFirst().getDefaultMessage();
        ErrorResponse errorResponse =
                ErrorResponse.of(e.getClass().getSimpleName(), errorMessage);
        GlobalApiResponse response = GlobalApiResponse.fail(status.value(), errorResponse);

        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<GlobalApiResponse> handleConstraintViolationException(
            ConstraintViolationException e
    ) {
        log.error("ConstraintViolationException : {}", e.getMessage(), e);

        Map<String, Object> bindingErrors = new HashMap<>();
        e.getConstraintViolations().forEach(
                constraintViolation -> {
                    List<String> propertyPath = List.of(
                            constraintViolation.getPropertyPath().toString().split("\\.")
                    );
                    String path = propertyPath.stream()
                            .skip(propertyPath.size() - 1L)
                            .findFirst()
                            .orElse(null);
                    bindingErrors.put(path, constraintViolation.getMessage());
                }
        );

        ErrorResponse errorResponse =
                ErrorResponse.of(e.getClass().getSimpleName(), bindingErrors.toString());
        GlobalApiResponse response =
                GlobalApiResponse.fail(HttpStatus.BAD_REQUEST.value(), errorResponse);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    protected ResponseEntity<GlobalApiResponse> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException e
    ) {
        log.error("MethodArgumentTypeMismatchException : {}", e.getMessage(), e);

        ErrorCode errorCode = ErrorCode.METHOD_ARGUMENT_TYPE_MISMATCH;
        HttpStatus status = errorHttpStatusMapper.toHttpStatus(errorCode);
        ErrorResponse errorResponse =
                ErrorResponse.of(e.getClass().getSimpleName(), errorCode.getMessage());
        GlobalApiResponse response =
                GlobalApiResponse.fail(status.value(), errorResponse);

        return ResponseEntity.status(status).body(response);
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException e,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        log.error("HttpRequestMethodNotSupportedException : {}", e.getMessage(), e);

        ErrorCode errorCode = ErrorCode.METHOD_NOT_ALLOWED;
        HttpStatus statusMapped = errorHttpStatusMapper.toHttpStatus(errorCode);
        ErrorResponse errorResponse =
                ErrorResponse.of(e.getClass().getSimpleName(), errorCode.getMessage());
        GlobalApiResponse response =
                GlobalApiResponse.fail(statusMapped.value(), errorResponse);

        return ResponseEntity.status(statusMapped).body(response);
    }

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<GlobalApiResponse> handleCustomException(CustomException e) {
        log.error("CustomException : {}", e.getMessage(), e);

        ErrorCode errorCode = e.getErrorCode();
        HttpStatus status = errorHttpStatusMapper.toHttpStatus(errorCode);
        ErrorResponse errorResponse =
                ErrorResponse.of(errorCode.getCode(), errorCode.getMessage());
        GlobalApiResponse response =
                GlobalApiResponse.fail(status.value(), errorResponse);

        return ResponseEntity.status(status).body(response);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            @NonNull HttpMessageNotReadableException e,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request
    ) {
        log.error("HttpMessageNotReadableException : {}", e.getMessage(), e);

        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause instanceof CustomException customException) {
                ErrorCode errorCode = customException.getErrorCode();
                HttpStatus statusMapped = errorHttpStatusMapper.toHttpStatus(errorCode);
                ErrorResponse errorResponse =
                        ErrorResponse.of(errorCode.getCode(), errorCode.getMessage());
                GlobalApiResponse response =
                        GlobalApiResponse.fail(statusMapped.value(), errorResponse);
                return ResponseEntity.status(statusMapped).body(response);
            }
            cause = cause.getCause();
        }

        ErrorResponse errorResponse =
                ErrorResponse.of(e.getClass().getSimpleName(), "잘못된 요청 형식입니다");
        GlobalApiResponse response =
                GlobalApiResponse.fail(HttpStatus.BAD_REQUEST.value(), errorResponse);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(Exception.class)
    protected ResponseEntity<GlobalApiResponse> handleException(Exception e) {
        log.error("Internal Server Error : {}", e.getMessage(), e);

        ErrorCode internalServerError = ErrorCode.INTERNAL_SERVER_ERROR;
        HttpStatus status = errorHttpStatusMapper.toHttpStatus(internalServerError);
        ErrorResponse errorResponse =
                ErrorResponse.of(e.getClass().getSimpleName(), internalServerError.getMessage());
        GlobalApiResponse response =
                GlobalApiResponse.fail(status.value(), errorResponse);

        return ResponseEntity.status(status).body(response);
    }
}
