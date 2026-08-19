package com.example.lowcode.audit.application;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class AuditCsvExporter {
    private static final String HEADER = "id,actor_user_id,actor_phone_masked,action,resource_type,resource_id,outcome,request_id,created_at";

    public byte[] toCsv(AuditLogService.AuditExport export) {
        StringBuilder csv = new StringBuilder("\uFEFF").append(HEADER).append("\r\n");
        for (AuditLogService.AuditView item : export.items()) {
            appendRow(csv,
                item.id(),
                item.actorUserId(),
                item.actorPhoneMasked(),
                item.action(),
                item.resourceType(),
                item.resourceId(),
                item.outcome(),
                item.requestId(),
                item.createdAt()
            );
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void appendRow(StringBuilder csv, Object... values) {
        for (int index = 0; index < values.length; index++) {
            if (index > 0) csv.append(',');
            csv.append(escape(values[index]));
        }
        csv.append("\r\n");
    }

    private String escape(Object value) {
        if (value == null) return "\"\"";
        String text = String.valueOf(value).replace("\"", "\"\"");
        return '"' + text + '"';
    }
}
