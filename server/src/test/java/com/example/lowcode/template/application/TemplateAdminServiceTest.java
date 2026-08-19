package com.example.lowcode.template.application;

import com.example.lowcode.audit.application.AuditLogService;
import com.example.lowcode.audit.infrastructure.AuditLogMapper;
import com.example.lowcode.auth.application.AuthRepository;
import com.example.lowcode.auth.security.CurrentUser;
import com.example.lowcode.common.exception.BusinessException;
import com.example.lowcode.template.infrastructure.DesignTemplateMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TemplateAdminServiceTest {
    @Test
    void operatorCanListButCannotChangeTemplateStatus() {
        DesignTemplateMapper mapper = mock(DesignTemplateMapper.class);
        when(mapper.findAdmin(null)).thenReturn(List.of(template(1001L, "PUBLISHED")));
        TemplateAdminService service = service("OPERATOR", mapper);

        assertThat(service.list(new CurrentUser(7, 11), null)).singleElement()
            .extracting(TemplateAdminService.TemplateView::status)
            .isEqualTo("PUBLISHED");
        assertThatThrownBy(() -> service.changeStatus(new CurrentUser(7, 11), 1001L, "DISABLED"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("仅管理员");
        verify(mapper, never()).updateStatus(anyLong(), anyString(), any());
    }

    @Test
    void administratorPublishesTemplateAndAuditsIt() {
        DesignTemplateMapper mapper = mock(DesignTemplateMapper.class);
        when(mapper.findAdminById(1001L)).thenReturn(template(1001L, "DRAFT"));
        when(mapper.updateStatus(anyLong(), anyString(), any())).thenReturn(1);
        CapturingAuditLogMapper auditMapper = new CapturingAuditLogMapper();
        TemplateAdminService service = service("ADMIN", mapper, auditMapper);

        TemplateAdminService.TemplateView result = service.changeStatus(
            new CurrentUser(7, 11), 1001L, "published"
        );

        assertThat(result.status()).isEqualTo("PUBLISHED");
        verify(mapper).updateStatus(anyLong(), anyString(), any(Instant.class));
        assertThat(auditMapper.row.action()).isEqualTo("TEMPLATE_STATUS_CHANGE");
        assertThat(auditMapper.row.resourceId()).isEqualTo("1001");
        assertThat(auditMapper.row.metadataJson()).contains("fromStatus").contains("toStatus");
    }

    @Test
    void publishingSetsPublishedAtAndOtherStatesClearIt() {
        DesignTemplateMapper mapper = mock(DesignTemplateMapper.class);
        when(mapper.findAdminById(1001L)).thenReturn(template(1001L, "PUBLISHED"));
        when(mapper.updateStatus(anyLong(), anyString(), isNull())).thenReturn(1);
        TemplateAdminService service = service("ADMIN", mapper);

        service.changeStatus(new CurrentUser(7, 11), 1001L, "DISABLED");

        verify(mapper).updateStatus(1001L, "DISABLED", null);
    }

    @Test
    void duplicateStatusIsIdempotentWithoutUpdateOrAudit() {
        DesignTemplateMapper mapper = mock(DesignTemplateMapper.class);
        when(mapper.findAdminById(1001L)).thenReturn(template(1001L, "PUBLISHED"));
        CapturingAuditLogMapper auditMapper = new CapturingAuditLogMapper();
        TemplateAdminService service = service("ADMIN", mapper, auditMapper);

        TemplateAdminService.TemplateView result = service.changeStatus(
            new CurrentUser(7, 11), 1001L, "PUBLISHED"
        );

        assertThat(result.status()).isEqualTo("PUBLISHED");
        verify(mapper, never()).updateStatus(anyLong(), anyString(), any());
        assertThat(auditMapper.row).isNull();
    }

    @Test
    void rejectsInvalidStatusAndUnknownTemplateBeforeMutation() {
        DesignTemplateMapper mapper = mock(DesignTemplateMapper.class);
        TemplateAdminService service = service("ADMIN", mapper);

        assertThatThrownBy(() -> service.changeStatus(new CurrentUser(7, 11), 1001L, "ARCHIVED"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("状态");
        assertThatThrownBy(() -> service.changeStatus(new CurrentUser(7, 11), 1001L, "PUBLISHED"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("模板不存在");
        verify(mapper, never()).updateStatus(anyLong(), anyString(), any());
    }

    private TemplateAdminService service(String role, DesignTemplateMapper mapper) {
        return service(role, mapper, new CapturingAuditLogMapper());
    }

    private TemplateAdminService service(String role, DesignTemplateMapper mapper, CapturingAuditLogMapper auditMapper) {
        AuthRepository repository = new AuthRepository() {
            @Override
            public Optional<UserIdentity> findByPhone(String phone) {
                return Optional.empty();
            }

            @Override
            public Optional<UserIdentity> findByUserAndTenant(long userId, long tenantId) {
                return Optional.of(new UserIdentity(userId, tenantId, "13800000000", "ACTIVE", "ACTIVE", role));
            }

            @Override
            public UserIdentity createUserWithDefaultTenant(String phone) {
                throw new UnsupportedOperationException();
            }
        };
        return new TemplateAdminService(
            repository,
            mapper,
            new AuditLogService(auditMapper, new ObjectMapper())
        );
    }

    private DesignTemplateMapper.AdminTemplateRow template(long id, String status) {
        DesignTemplateMapper.AdminTemplateRow row = new DesignTemplateMapper.AdminTemplateRow();
        row.setId(id);
        row.setName("朋友圈促销");
        row.setWidth(1080);
        row.setHeight(1440);
        row.setStatus(status);
        row.setUpdatedAt(Instant.parse("2026-08-19T00:00:00Z"));
        return row;
    }

    private static final class CapturingAuditLogMapper implements AuditLogMapper {
        private AuditLogRow row;

        @Override
        public int insert(AuditLogRow row) {
            this.row = row;
            row.setId(1L);
            return 1;
        }

        @Override
        public List<AuditLogViewRow> findPage(long tenantId, String action, String outcome, java.sql.Timestamp from, java.sql.Timestamp to, int pageSize, long offset) {
            return List.of();
        }

        @Override
        public long countPage(long tenantId, String action, String outcome, java.sql.Timestamp from, java.sql.Timestamp to) {
            return 0;
        }
    }
}
