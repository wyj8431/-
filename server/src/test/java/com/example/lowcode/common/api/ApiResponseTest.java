package com.example.lowcode.common.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {
    @Test
    void successCarriesCodeDataAndTraceId() {
        ApiResponse<String> response = ApiResponse.success("saved", "trace-1");

        assertThat(response.code()).isEqualTo("OK");
        assertThat(response.message()).isEqualTo("success");
        assertThat(response.data()).isEqualTo("saved");
        assertThat(response.traceId()).isEqualTo("trace-1");
    }
}
