package com.example.lowcode.template.application;

import com.example.lowcode.audit.application.AuditLogService;
import com.example.lowcode.audit.infrastructure.AuditLogMapper;
import com.example.lowcode.auth.application.AuthRepository;
import com.example.lowcode.auth.security.CurrentUser;
import com.example.lowcode.common.exception.BusinessException;
import com.example.lowcode.template.infrastructure.TemplateCategoryMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TemplateCategoryAdminServiceTest {
    @Test
    void operatorCanListAllStatusesButCannotChangeThem() {
        TemplateCategoryMapper mapper = mock(TemplateCategoryMapper.class);
        when(mapper.findAll(null)).thenReturn(List.of(category("marketing", "PUBLISHED")));
        TemplateCategoryAdminService service = service("OPERATOR", mapper);

        assertThat(service.list(new CurrentUser(7, 11), null)).singleElement()
            .extracting(TemplateCategoryAdminService.CategoryView::status)
            .isEqualTo("PUBLISHED");
        assertThatThrownBy(() -> service.changeStatus(new CurrentUser(7, 11), "marketing", "DISABLED"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("仅管理员");
    }

    @Test
    void administratorChangesStatusAndWritesAuditEvent() {
        TemplateCategoryMapper mapper = mock(TemplateCategoryMapper.class);
        when(mapper.findAll(null)).thenReturn(List.of(category("marketing", "DRAFT")));
        when(mapper.updateStatus("marketing", "PUBLISHED")).thenReturn(1);
        CapturingAuditLogMapper auditMapper = new CapturingAuditLogMapper();
        TemplateCategoryAdminService service = service("ADMIN", mapper, auditMapper);

        TemplateCategoryAdminService.CategoryView result = service.changeStatus(
            new CurrentUser(7, 11), "marketing", "published"
        );

        assertThat(result.status()).isEqualTo("PUBLISHED");
        verify(mapper).updateStatus("marketing", "PUBLISHED");
        assertThat(auditMapper.row.action()).isEqualTo("TEMPLATE_CATEGORY_STATUS_CHANGE");
        assertThat(auditMapper.row.metadataJson()).contains("fromStatus").contains("toStatus");
    }

    @Test
    void rejectsUnknownStatusBeforeMutatingCategory() {
        TemplateCategoryMapper mapper = mock(TemplateCategoryMapper.class);
        TemplateCategoryAdminService service = service("ADMIN", mapper);

        assertThatThrownBy(() -> service.changeStatus(new CurrentUser(7, 11), "marketing", "ARCHIVED"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("状态");
        verify(mapper, org.mockito.Mockito.never()).updateStatus(anyString(), anyString());
    }

    private TemplateCategoryAdminService service(String role, TemplateCategoryMapper mapper) {
        return service(role, mapper, new CapturingAuditLogMapper());
    }

    private TemplateCategoryAdminService service(String role, TemplateCategoryMapper mapper, CapturingAuditLogMapper auditMapper) {
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
        return new TemplateCategoryAdminService(
            repository,
            mapper,
            new AuditLogService(auditMapper, new ObjectMapper())
        );
    }

    private TemplateCategoryMapper.AdminCategoryRow category(String code, String status) {
        TemplateCategoryMapper.AdminCategoryRow row = new TemplateCategoryMapper.AdminCategoryRow();
        row.setId(10L);
        row.setCode(code);
        row.setName("营销推广");
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
