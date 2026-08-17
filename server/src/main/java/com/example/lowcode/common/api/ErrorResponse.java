package com.example.lowcode.common.api;

import java.util.Map;

public record ErrorResponse(String code, String message, String traceId, Map<String, Object> details) {
}
