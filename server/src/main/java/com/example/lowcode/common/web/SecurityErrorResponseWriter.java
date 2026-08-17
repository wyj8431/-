package com.example.lowcode.common.web;

import com.example.lowcode.common.api.ErrorResponse;
import com.example.lowcode.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
public class SecurityErrorResponseWriter {
    private final ObjectMapper objectMapper;

    public SecurityErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode errorCode) throws IOException {
        String traceId = traceId(request, response);
        response.setStatus(errorCode.httpStatus().value());
        response.setHeader(TraceIdFilter.TRACE_ID_HEADER, traceId);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
            response.getOutputStream(),
            new ErrorResponse(errorCode.name(), errorCode.defaultMessage(), traceId, null)
        );
    }

    private String traceId(HttpServletRequest request, HttpServletResponse response) {
        Object value = request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
        if (value instanceof String traceId) {
            return traceId;
        }
        String traceId = UUID.randomUUID().toString();
        request.setAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE, traceId);
        return traceId;
    }
}
