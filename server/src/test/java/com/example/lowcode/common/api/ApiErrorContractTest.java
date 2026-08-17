package com.example.lowcode.common.api;

import com.example.lowcode.common.exception.BusinessException;
import com.example.lowcode.common.exception.ErrorCode;
import com.example.lowcode.common.exception.GlobalExceptionHandler;
import com.example.lowcode.common.web.TraceIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class ApiErrorContractTest {
    @Test
    void businessExceptionMapsToStableConflictResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE, "trace-1");
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<ErrorResponse> response = handler.handleBusinessException(
            new BusinessException(ErrorCode.DESIGN_VERSION_CONFLICT, "设计稿已被更新"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody())
            .isEqualTo(new ErrorResponse("DESIGN_VERSION_CONFLICT", "设计稿已被更新", "trace-1", null));
    }

    @Test
    void traceIdFilterKeepsValidIncomingTraceId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "request-42");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new TraceIdFilter().doFilter(request, response, new MockFilterChain());

        assertThat(request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE)).isEqualTo("request-42");
        assertThat(response.getHeader(TraceIdFilter.TRACE_ID_HEADER)).isEqualTo("request-42");
    }

    @Test
    void malformedJsonDoesNotExposeInternalExceptionDetails() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE, "trace-2");
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<ErrorResponse> response = handler.handleMalformedJson(
            new org.springframework.http.converter.HttpMessageNotReadableException(
                "invalid json", new RuntimeException("invalid"), new MockHttpInputMessage(new byte[0])
            ), request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().traceId()).isEqualTo("trace-2");
        assertThat(response.getBody().message()).doesNotContain("invalid json");
    }
}
