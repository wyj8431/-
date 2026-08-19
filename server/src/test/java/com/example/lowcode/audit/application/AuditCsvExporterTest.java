package com.example.lowcode.audit.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuditCsvExporterTest {
    @Test
    void writesUtf8BomFixedHeadersAndEscapedFields() {
        AuditCsvExporter exporter = new AuditCsvExporter();
        byte[] csv = exporter.toCsv(new AuditLogService.AuditExport(
            List.of(new AuditLogService.AuditView(
                9L, 7L, "138****0000", "LOGIN", "AUTH_SESSION", "a,\"b\nresource",
                "SUCCESS", "trace-9", Instant.parse("2026-08-18T09:00:00Z")
            )),
            1L,
            false
        ));

        String text = new String(csv, StandardCharsets.UTF_8);
        assertThat(text).startsWith("\uFEFFid,actor_user_id,actor_phone_masked,action,resource_type,resource_id,outcome,request_id,created_at\r\n");
        assertThat(text).contains("\"a,\"\"b\nresource\"");
        assertThat(text).contains("138****0000").doesNotContain("13800000000");
    }
}
