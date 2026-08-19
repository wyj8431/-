package com.example.lowcode.audit.application;

import com.example.lowcode.audit.infrastructure.AuditLogMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditLogServiceTest {
    private final CapturingAuditLogMapper mapper = new CapturingAuditLogMapper();
    private final AuditLogService service = new AuditLogService(mapper, new ObjectMapper());

    @Test
    void recordsStructuredEventAndRedactsSensitiveMetadata() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("phone", "13800000000");
        metadata.put("refreshToken", "raw-refresh-token");
        metadata.put("cookie", "refresh_cookie=secret");
        metadata.put("verificationCode", "123456");
        metadata.put("tenantRole", "ADMIN");
        metadata.put("attempt", 1);

        service.record(new AuditLogService.AuditEvent(
            7L,
            11L,
            "LOGIN",
            "AUTH_SESSION",
            "7",
            AuditLogService.Outcome.SUCCESS,
            "trace-1",
            metadata
        ));

        assertThat(mapper.row.actorUserId()).isEqualTo(7L);
        assertThat(mapper.row.tenantId()).isEqualTo(11L);
        assertThat(mapper.row.action()).isEqualTo("LOGIN");
        assertThat(mapper.row.outcome()).isEqualTo("SUCCESS");
        assertThat(mapper.row.metadataJson()).contains("\"tenantRole\":\"ADMIN\"");
        assertThat(mapper.row.metadataJson()).contains("\"attempt\":1");
        assertThat(mapper.row.metadataJson()).doesNotContain("13800000000");
        assertThat(mapper.row.metadataJson()).doesNotContain("raw-refresh-token");
        assertThat(mapper.row.metadataJson()).doesNotContain("refresh_cookie=secret");
        assertThat(mapper.row.metadataJson()).doesNotContain("123456");
    }

    @Test
    void pagesTenantScopedEventsWithNormalizedFiltersAndMaskedActor() {
        AuditLogMapper.AuditLogViewRow row = new AuditLogMapper.AuditLogViewRow();
        row.setId(9L);
        row.setActorUserId(7L);
        row.setActorPhone("13800000000");
        row.setAction("LOGIN");
        row.setResourceType("AUTH_SESSION");
        row.setResourceId("7");
        row.setOutcome("SUCCESS");
        row.setRequestId("trace-9");
        row.setCreatedAt(Timestamp.from(Instant.parse("2026-08-18T09:00:00Z")));
        mapper.viewRows = List.of(row);

        AuditLogService.AuditPage page = service.page(11L, new AuditLogService.AuditQuery(
            2, 10, " login ", " success ", "2026-08-18T00:00:00Z", "2026-08-19T00:00:00Z"
        ));

        assertThat(page.total()).isEqualTo(1L);
        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.actorPhoneMasked()).isEqualTo("138****0000");
            assertThat(item.action()).isEqualTo("LOGIN");
            assertThat(item.createdAt()).isEqualTo(Instant.parse("2026-08-18T09:00:00Z"));
        });
        assertThat(mapper.tenantId).isEqualTo(11L);
        assertThat(mapper.action).isEqualTo("LOGIN");
        assertThat(mapper.outcome).isEqualTo("SUCCESS");
        assertThat(mapper.pageSize).isEqualTo(10);
        assertThat(mapper.offset).isEqualTo(10L);
        assertThat(mapper.from).isEqualTo(Timestamp.from(Instant.parse("2026-08-18T00:00:00Z")));
        assertThat(mapper.to).isEqualTo(Timestamp.from(Instant.parse("2026-08-19T00:00:00Z")));
    }

    @Test
    void exportsNormalizedFiltersWithBoundedRowsAndTruncationMetadata() {
        AuditLogMapper.AuditLogViewRow row = new AuditLogMapper.AuditLogViewRow();
        row.setId(9L);
        row.setActorPhone("13800000000");
        row.setAction("LOGIN");
        row.setResourceType("AUTH_SESSION");
        row.setOutcome("SUCCESS");
        mapper.viewRows = List.of(row);
        mapper.count = 5001L;

        AuditLogService.AuditExport result = service.export(11L, new AuditLogService.AuditExportQuery(
            " login ", " success ", "2026-08-18T00:00:00Z", "2026-08-19T00:00:00Z"
        ));

        assertThat(result.items()).hasSize(1);
        assertThat(result.total()).isEqualTo(5001L);
        assertThat(result.truncated()).isTrue();
        assertThat(mapper.tenantId).isEqualTo(11L);
        assertThat(mapper.action).isEqualTo("LOGIN");
        assertThat(mapper.outcome).isEqualTo("SUCCESS");
        assertThat(mapper.pageSize).isEqualTo(5000);
        assertThat(mapper.offset).isZero();
    }

    private static final class CapturingAuditLogMapper implements AuditLogMapper {
        private AuditLogMapper.AuditLogRow row;
        private List<AuditLogMapper.AuditLogViewRow> viewRows = List.of();
        private long tenantId;
        private long count;
        private String action;
        private String outcome;
        private Timestamp from;
        private Timestamp to;
        private int pageSize;
        private long offset;

        @Override
        public int insert(AuditLogMapper.AuditLogRow row) {
            this.row = row;
            row.setId(1L);
            return 1;
        }

        @Override
        public List<AuditLogMapper.AuditLogViewRow> findPage(
            long tenantId,
            String action,
            String outcome,
            Timestamp from,
            Timestamp to,
            int pageSize,
            long offset
        ) {
            this.tenantId = tenantId;
            this.action = action;
            this.outcome = outcome;
            this.from = from;
            this.to = to;
            this.pageSize = pageSize;
            this.offset = offset;
            return viewRows;
        }

        @Override
        public long countPage(long tenantId, String action, String outcome, Timestamp from, Timestamp to) {
            return count == 0 ? viewRows.size() : count;
        }
    }
}
