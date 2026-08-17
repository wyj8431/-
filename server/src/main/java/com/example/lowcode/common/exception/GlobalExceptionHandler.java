package com.example.lowcode.common.exception;

import com.example.lowcode.common.api.ErrorResponse;
import com.example.lowcode.common.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
        BusinessException exception,
        HttpServletRequest request
    ) {
        return response(exception.errorCode(), exception.getMessage(), traceId(request), exception.details());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
        MethodArgumentNotValidException exception,
        HttpServletRequest request
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            details.put(error.getField(), error.getDefaultMessage());
        }
        return response(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(), traceId(request), details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
        ConstraintViolationException exception,
        HttpServletRequest request
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(violation ->
            details.put(violation.getPropertyPath().toString(), violation.getMessage())
        );
        return response(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(), traceId(request), details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedJson(
        HttpMessageNotReadableException exception,
        HttpServletRequest request
    ) {
        return response(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(), traceId(request), null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
        NoResourceFoundException exception,
        HttpServletRequest request
    ) {
        return response(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.defaultMessage(), traceId(request), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception exception, HttpServletRequest request) {
        String traceId = traceId(request);
        LOGGER.error("Unhandled request failure, traceId={}", traceId, exception);
        return response(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage(), traceId, null);
    }

    private ResponseEntity<ErrorResponse> response(
        ErrorCode errorCode,
        String message,
        String traceId,
        Map<String, Object> details
    ) {
        return ResponseEntity.status(errorCode.httpStatus())
            .body(new ErrorResponse(errorCode.name(), message, traceId, details));
    }

    private String traceId(HttpServletRequest request) {
        Object value = request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
        return value instanceof String traceId ? traceId : UUID.randomUUID().toString();
    }
}
