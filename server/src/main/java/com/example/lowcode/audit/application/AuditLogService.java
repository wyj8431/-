package com.example.lowcode.audit.application;

import com.example.lowcode.audit.infrastructure.AuditLogMapper;
import com.example.lowcode.common.exception.BusinessException;
import com.example.lowcode.common.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Array;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class AuditLogService {
    public static final int MAX_EXPORT_ROWS = 5_000;
    private static final String REDACTED = "[REDACTED]";
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");

    private final AuditLogMapper mapper;
    private final ObjectMapper objectMapper;

    public AuditLogService(AuditLogMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void record(AuditEvent event) {
        AuditLogMapper.AuditLogRow row = new AuditLogMapper.AuditLogRow();
        row.setActorUserId(event.actorUserId());
        row.setTenantId(event.tenantId());
        row.setAction(requireText(event.action(), "action"));
        row.setResourceType(requireText(event.resourceType(), "resourceType"));
        row.setResourceId(event.resourceId());
        row.setOutcome(event.outcome().name());
        row.setRequestId(event.requestId());
        row.setMetadataJson(serializeMetadata(event.metadata()));
        if (mapper.insert(row) != 1 || row.id() == null) {
            throw new IllegalStateException("Could not write audit log");
        }
    }

    @Transactional(readOnly = true)
    public AuditPage page(long tenantId, AuditQuery query) {
        if (tenantId <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "团队标识无效");
        }
        NormalizedAuditQuery normalized = normalize(query);
        long offset = (long) (normalized.page() - 1) * normalized.pageSize();
        List<AuditView> items = mapper.findPage(
                tenantId,
                normalized.action(),
                normalized.outcome(),
                normalized.from() == null ? null : Timestamp.from(normalized.from()),
                normalized.to() == null ? null : Timestamp.from(normalized.to()),
                normalized.pageSize(),
                offset
            ).stream()
            .map(this::toView)
            .toList();
        return new AuditPage(items, normalized.page(), normalized.pageSize(), mapper.countPage(
            tenantId,
            normalized.action(),
            normalized.outcome(),
            normalized.from() == null ? null : Timestamp.from(normalized.from()),
            normalized.to() == null ? null : Timestamp.from(normalized.to())
        ));
    }

    @Transactional(readOnly = true)
    public AuditExport export(long tenantId, AuditExportQuery query) {
        if (tenantId <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "团队标识无效");
        }
        if (query == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "导出条件无效");
        }
        NormalizedAuditQuery normalized = normalize(new AuditQuery(
            1, 1, query.action(), query.outcome(), query.from(), query.to()
        ));
        Timestamp from = normalized.from() == null ? null : Timestamp.from(normalized.from());
        Timestamp to = normalized.to() == null ? null : Timestamp.from(normalized.to());
        List<AuditView> items = mapper.findPage(
                tenantId, normalized.action(), normalized.outcome(), from, to, MAX_EXPORT_ROWS, 0
            ).stream()
            .map(this::toView)
            .toList();
        long total = mapper.countPage(tenantId, normalized.action(), normalized.outcome(), from, to);
        return new AuditExport(items, total, total > MAX_EXPORT_ROWS);
    }

    private NormalizedAuditQuery normalize(AuditQuery query) {
        if (query == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "查询条件无效");
        }
        if (query.page() < 1 || query.page() > 100_000) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "页码无效");
        }
        if (query.pageSize() < 1 || query.pageSize() > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "每页数量无效");
        }
        String action = normalizeAction(query.action());
        String outcome = normalizeOutcome(query.outcome());
        Instant from = parseInstant(query.from(), "开始时间");
        Instant to = parseInstant(query.to(), "结束时间");
        if (from != null && to != null && !from.isBefore(to)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "时间范围无效");
        }
        return new NormalizedAuditQuery(query.page(), query.pageSize(), action, outcome, from, to);
    }

    private String normalizeAction(String action) {
        if (action == null || action.isBlank()) {
            return null;
        }
        String normalized = action.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z][A-Z0-9_]{0,63}")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "审计动作无效");
        }
        return normalized;
    }

    private String normalizeOutcome(String outcome) {
        if (outcome == null || outcome.isBlank()) {
            return null;
        }
        String normalized = outcome.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("SUCCESS", "FAILURE").contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "审计结果无效");
        }
        return normalized;
    }

    private Instant parseInstant(String value, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, label + "格式无效");
        }
    }

    private AuditView toView(AuditLogMapper.AuditLogViewRow row) {
        Timestamp createdAt = row.getCreatedAt();
        return new AuditView(
            row.getId(),
            row.getActorUserId(),
            row.getActorPhone() == null ? "系统" : maskPhone(row.getActorPhone()),
            row.getAction(),
            row.getResourceType(),
            row.getResourceId(),
            row.getOutcome(),
            row.getRequestId(),
            createdAt == null ? null : createdAt.toInstant()
        );
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return "****";
        }
        if (phone.length() <= 7) {
            return "****" + phone.substring(Math.max(0, phone.length() - 2));
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private String serializeMetadata(Map<String, ?> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(sanitizeMap(metadata));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Audit metadata is not serializable", exception);
        }
    }

    private Map<String, Object> sanitizeMap(Map<String, ?> metadata) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            String name = String.valueOf(key);
            sanitized.put(name, isSensitiveKey(name) ? REDACTED : sanitizeValue(value));
        });
        return sanitized;
    }

    private Object sanitizeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String string) {
            return PHONE.matcher(string).replaceAll(REDACTED);
        }
        if (value instanceof Map<?, ?> nested) {
            Map<String, Object> mapped = new LinkedHashMap<>();
            nested.forEach((key, nestedValue) -> {
                String name = String.valueOf(key);
                mapped.put(name, isSensitiveKey(name) ? REDACTED : sanitizeValue(nestedValue));
            });
            return mapped;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> values = new ArrayList<>();
            iterable.forEach(item -> values.add(sanitizeValue(item)));
            return values;
        }
        if (value.getClass().isArray()) {
            List<Object> values = new ArrayList<>();
            for (int index = 0; index < Array.getLength(value); index++) {
                values.add(sanitizeValue(Array.get(value, index)));
            }
            return values;
        }
        return value;
    }

    private boolean isSensitiveKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        return normalized.contains("token")
            || normalized.contains("cookie")
            || normalized.contains("phone")
            || normalized.contains("verification")
            || normalized.contains("password")
            || normalized.contains("secret")
            || normalized.contains("authorization")
            || normalized.equals("code")
            || normalized.endsWith("code");
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Audit " + field + " must not be blank");
        }
        return value;
    }

    public enum Outcome {
        SUCCESS,
        FAILURE
    }

    public record AuditQuery(
        int page,
        int pageSize,
        String action,
        String outcome,
        String from,
        String to
    ) {
    }

    public record AuditExportQuery(
        String action,
        String outcome,
        String from,
        String to
    ) {
    }

    private record NormalizedAuditQuery(
        int page,
        int pageSize,
        String action,
        String outcome,
        Instant from,
        Instant to
    ) {
    }

    public record AuditPage(List<AuditView> items, int page, int pageSize, long total) {
        public AuditPage {
            items = List.copyOf(items);
        }
    }

    public record AuditExport(List<AuditView> items, long total, boolean truncated) {
        public AuditExport {
            items = List.copyOf(items);
        }
    }

    public record AuditView(
        long id,
        Long actorUserId,
        String actorPhoneMasked,
        String action,
        String resourceType,
        String resourceId,
        String outcome,
        String requestId,
        Instant createdAt
    ) {
    }

    public record AuditEvent(
        Long actorUserId,
        Long tenantId,
        String action,
        String resourceType,
        String resourceId,
        Outcome outcome,
        String requestId,
        Map<String, ?> metadata
    ) {
        public AuditEvent {
            if (outcome == null) {
                throw new IllegalArgumentException("Audit outcome must not be null");
            }
        }
    }
}
