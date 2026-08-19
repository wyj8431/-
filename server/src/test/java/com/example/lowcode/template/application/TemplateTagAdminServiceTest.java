package com.example.lowcode.template.application;

import com.example.lowcode.audit.application.AuditLogService;
import com.example.lowcode.audit.infrastructure.AuditLogMapper;
import com.example.lowcode.auth.application.AuthRepository;
import com.example.lowcode.auth.security.CurrentUser;
import com.example.lowcode.common.exception.BusinessException;
import com.example.lowcode.template.infrastructure.TemplateTagMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TemplateTagAdminServiceTest {
    @Test
    void operatorCanListButCannotChangeTagStatus() {
        TemplateTagMapper mapper = mock(TemplateTagMapper.class);
        when(mapper.findAll(null)).thenReturn(List.of(tag("promotion", "PUBLISHED")));
        TemplateTagAdminService service = service("OPERATOR", mapper);

        assertThat(service.list(new CurrentUser(7, 11), null)).singleElement()
            .extracting(TemplateTagAdminService.TagView::status)
            .isEqualTo("PUBLISHED");
        assertThatThrownBy(() -> service.changeStatus(new CurrentUser(7, 11), "promotion", "DISABLED"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("仅管理员");
        verify(mapper, never()).updateStatus(anyString(), anyString());
    }

    @Test
    void administratorChangesStatusAndWritesAuditEvent() {
        TemplateTagMapper mapper = mock(TemplateTagMapper.class);
        when(mapper.findByCode("promotion")).thenReturn(tag("promotion", "DRAFT"));
        when(mapper.updateStatus("promotion", "PUBLISHED")).thenReturn(1);
        CapturingAuditLogMapper auditMapper = new CapturingAuditLogMapper();
        TemplateTagAdminService service = service("ADMIN", mapper, auditMapper);

        TemplateTagAdminService.TagView result = service.changeStatus(
            new CurrentUser(7, 11), "promotion", "published"
        );

        assertThat(result.status()).isEqualTo("PUBLISHED");
        verify(mapper).updateStatus("promotion", "PUBLISHED");
        assertThat(auditMapper.row.action()).isEqualTo("TEMPLATE_TAG_STATUS_CHANGE");
        assertThat(auditMapper.row.resourceType()).isEqualTo("TEMPLATE_TAG");
        assertThat(auditMapper.row.metadataJson()).contains("fromStatus").contains("toStatus");
    }

    @Test
    void duplicateStatusIsIdempotentWithoutUpdateOrAudit() {
        TemplateTagMapper mapper = mock(TemplateTagMapper.class);
        when(mapper.findByCode("promotion")).thenReturn(tag("promotion", "PUBLISHED"));
        CapturingAuditLogMapper auditMapper = new CapturingAuditLogMapper();
        TemplateTagAdminService service = service("ADMIN", mapper, auditMapper);

        assertThat(service.changeStatus(new CurrentUser(7, 11), "promotion", "PUBLISHED").status())
            .isEqualTo("PUBLISHED");
        verify(mapper, never()).updateStatus(anyString(), anyString());
        assertThat(auditMapper.row).isNull();
    }

    @Test
    void rejectsInvalidStatusAndCodeBeforeMutatingTag() {
        TemplateTagMapper mapper = mock(TemplateTagMapper.class);
        TemplateTagAdminService service = service("ADMIN", mapper);

        assertThatThrownBy(() -> service.changeStatus(new CurrentUser(7, 11), "promotion", "ARCHIVED"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("状态");
        assertThatThrownBy(() -> service.changeStatus(new CurrentUser(7, 11), "Bad_Code", "PUBLISHED"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("编码");
        verify(mapper, never()).updateStatus(anyString(), anyString());
    }

    @Test
    void returnsNotFoundForUnknownTag() {
        TemplateTagMapper mapper = mock(TemplateTagMapper.class);
        when(mapper.findByCode("missing")).thenReturn(null);
        TemplateTagAdminService service = service("ADMIN", mapper);

        assertThatThrownBy(() -> service.changeStatus(new CurrentUser(7, 11), "missing", "PUBLISHED"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("标签不存在");
    }

    private TemplateTagAdminService service(String role, TemplateTagMapper mapper) {
        return service(role, mapper, new CapturingAuditLogMapper());
    }

    private TemplateTagAdminService service(String role, TemplateTagMapper mapper, CapturingAuditLogMapper auditMapper) {
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
        return new TemplateTagAdminService(
            repository,
            mapper,
            new AuditLogService(auditMapper, new ObjectMapper())
        );
    }

    private TemplateTagMapper.AdminTagRow tag(String code, String status) {
        TemplateTagMapper.AdminTagRow row = new TemplateTagMapper.AdminTagRow();
        row.setId(20L);
        row.setCode(code);
        row.setName("促销");
        row.setSortOrder(10);
        row.setStatus(status);
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
