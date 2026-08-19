package com.example.lowcode.template.application;

import com.example.lowcode.audit.application.AuditLogService;
import com.example.lowcode.audit.infrastructure.AuditLogMapper;
import com.example.lowcode.auth.application.AuthRepository;
import com.example.lowcode.auth.security.CurrentUser;
import com.example.lowcode.common.exception.BusinessException;
import com.example.lowcode.common.exception.ErrorCode;
import com.example.lowcode.template.infrastructure.DesignTemplateMapper;
import com.example.lowcode.template.infrastructure.TemplateCategoryMapper;
import com.example.lowcode.template.infrastructure.TemplateTagMapper;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

    @Test
    void administratorCreatesDraftTemplateWithStarterSchemaAndTags() throws Exception {
        DesignTemplateMapper mapper = mock(DesignTemplateMapper.class);
        TemplateCategoryMapper categoryMapper = mock(TemplateCategoryMapper.class);
        TemplateTagMapper tagMapper = mock(TemplateTagMapper.class);
        when(categoryMapper.findByCode("marketing")).thenReturn(category(10L, "marketing"));
        when(tagMapper.findAdminByCodes(List.of("promotion", "seasonal")))
            .thenReturn(List.of(tag(20L, "promotion"), tag(21L, "seasonal")));
        when(mapper.insert(any(DesignTemplateMapper.AdminTemplateRow.class))).thenAnswer(invocation -> {
            DesignTemplateMapper.AdminTemplateRow row = invocation.getArgument(0);
            row.setId(1002L);
            return 1;
        });
        when(tagMapper.insertRelations(1002L, List.of(20L, 21L))).thenReturn(2);
        CapturingAuditLogMapper auditMapper = new CapturingAuditLogMapper();
        TemplateAdminService service = service("ADMIN", mapper, categoryMapper, tagMapper, auditMapper);

        TemplateAdminService.TemplateView result = service.create(
            new CurrentUser(7, 11), " 节日促销 ", 1080, 1440, "marketing", List.of("promotion", "seasonal"), 20
        );

        assertThat(result.id()).isEqualTo(1002L);
        assertThat(result.name()).isEqualTo("节日促销");
        assertThat(result.status()).isEqualTo("DRAFT");
        assertThat(result.categoryCode()).isEqualTo("marketing");
        assertThat(result.tagCodes()).containsExactly("promotion", "seasonal");
        org.mockito.ArgumentCaptor<DesignTemplateMapper.AdminTemplateRow> captor = org.mockito.ArgumentCaptor.forClass(DesignTemplateMapper.AdminTemplateRow.class);
        verify(mapper).insert(captor.capture());
        DesignTemplateMapper.AdminTemplateRow inserted = captor.getValue();
        assertThat(new ObjectMapper().readTree(inserted.getSchemaJson()).path("schemaVersion").asInt()).isEqualTo(1);
        verify(tagMapper).insertRelations(1002L, List.of(20L, 21L));
        assertThat(auditMapper.row.action()).isEqualTo("TEMPLATE_CREATE");
        assertThat(auditMapper.row.metadataJson()).contains("tagCodes", "promotion", "DRAFT");
    }

    @Test
    void administratorUpdatesMetadataAndReplacesTagAssociations() {
        DesignTemplateMapper mapper = mock(DesignTemplateMapper.class);
        TemplateCategoryMapper categoryMapper = mock(TemplateCategoryMapper.class);
        TemplateTagMapper tagMapper = mock(TemplateTagMapper.class);
        when(mapper.findAdminById(1001L)).thenReturn(template(1001L, "DRAFT"));
        when(categoryMapper.findByCode("marketing")).thenReturn(category(10L, "marketing"));
        when(tagMapper.findAdminByCodes(List.of("seasonal"))).thenReturn(List.of(tag(21L, "seasonal")));
        when(tagMapper.findByTemplateId(1001L)).thenReturn(List.of(tag(20L, "promotion")));
        when(mapper.updateDetails(eq(1001L), eq("夏日活动"), eq(1200), eq(1600), eq(10L), isNull(), eq(30)))
            .thenReturn(1);
        when(tagMapper.deleteRelations(1001L)).thenReturn(1);
        when(tagMapper.insertRelations(1001L, List.of(21L))).thenReturn(1);
        CapturingAuditLogMapper auditMapper = new CapturingAuditLogMapper();
        TemplateAdminService service = service("ADMIN", mapper, categoryMapper, tagMapper, auditMapper);

        TemplateAdminService.TemplateView result = service.update(
            new CurrentUser(7, 11), 1001L, " 夏日活动 ", 1200, 1600, "marketing", List.of("seasonal"), 30
        );

        assertThat(result.name()).isEqualTo("夏日活动");
        assertThat(result.width()).isEqualTo(1200);
        assertThat(result.tagCodes()).containsExactly("seasonal");
        verify(mapper).updateDetails(1001L, "夏日活动", 1200, 1600, 10L, null, 30);
        verify(tagMapper).deleteRelations(1001L);
        verify(tagMapper).insertRelations(1001L, List.of(21L));
        assertThat(auditMapper.rows).extracting(AuditLogMapper.AuditLogRow::action)
            .contains("TEMPLATE_TAG_ASSOCIATIONS_REPLACE", "TEMPLATE_UPDATE");
    }

    @Test
    void administratorDeletesUnreferencedTemplateAndAuditsIt() {
        DesignTemplateMapper mapper = mock(DesignTemplateMapper.class);
        TemplateCategoryMapper categoryMapper = mock(TemplateCategoryMapper.class);
        TemplateTagMapper tagMapper = mock(TemplateTagMapper.class);
        when(mapper.findAdminById(1002L)).thenReturn(template(1002L, "DRAFT"));
        when(mapper.countReferences(1002L)).thenReturn(0);
        when(mapper.deleteById(1002L)).thenReturn(1);
        CapturingAuditLogMapper auditMapper = new CapturingAuditLogMapper();
        TemplateAdminService service = service("ADMIN", mapper, categoryMapper, tagMapper, auditMapper);

        service.delete(new CurrentUser(7, 11), 1002L);

        verify(mapper).countReferences(1002L);
        verify(mapper).deleteById(1002L);
        assertThat(auditMapper.row.action()).isEqualTo("TEMPLATE_DELETE");
        assertThat(auditMapper.row.resourceId()).isEqualTo("1002");
    }

    @Test
    void refusesToDeleteTemplateWithReferences() {
        DesignTemplateMapper mapper = mock(DesignTemplateMapper.class);
        TemplateCategoryMapper categoryMapper = mock(TemplateCategoryMapper.class);
        TemplateTagMapper tagMapper = mock(TemplateTagMapper.class);
        when(mapper.findAdminById(1001L)).thenReturn(template(1001L, "PUBLISHED"));
        when(mapper.countReferences(1001L)).thenReturn(1);
        TemplateAdminService service = service("ADMIN", mapper, categoryMapper, tagMapper);

        assertThatThrownBy(() -> service.delete(new CurrentUser(7, 11), 1001L))
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).errorCode())
            .isEqualTo(ErrorCode.TEMPLATE_IN_USE);
        verify(mapper, never()).deleteById(1001L);
        verifyNoInteractions(tagMapper);
    }

    @Test
    void operatorCannotCreateUpdateOrDeleteTemplates() {
        DesignTemplateMapper mapper = mock(DesignTemplateMapper.class);
        TemplateCategoryMapper categoryMapper = mock(TemplateCategoryMapper.class);
        TemplateTagMapper tagMapper = mock(TemplateTagMapper.class);
        TemplateAdminService service = service("OPERATOR", mapper, categoryMapper, tagMapper);
        CurrentUser operator = new CurrentUser(7, 11);

        assertThatThrownBy(() -> service.create(operator, "模板", 1080, 1440, null, List.of(), null))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("仅管理员");
        assertThatThrownBy(() -> service.update(operator, 1001L, "模板", 1080, 1440, null, List.of(), null))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("仅管理员");
        assertThatThrownBy(() -> service.delete(operator, 1001L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("仅管理员");

        verifyNoInteractions(mapper, categoryMapper, tagMapper);
    }

    private TemplateAdminService service(String role, DesignTemplateMapper mapper) {
        return service(role, mapper, mock(TemplateCategoryMapper.class), mock(TemplateTagMapper.class), new CapturingAuditLogMapper());
    }

    private TemplateAdminService service(String role, DesignTemplateMapper mapper, CapturingAuditLogMapper auditMapper) {
        return service(role, mapper, mock(TemplateCategoryMapper.class), mock(TemplateTagMapper.class), auditMapper);
    }

    private TemplateAdminService service(
        String role,
        DesignTemplateMapper mapper,
        TemplateCategoryMapper categoryMapper,
        TemplateTagMapper tagMapper
    ) {
        return service(role, mapper, categoryMapper, tagMapper, new CapturingAuditLogMapper());
    }

    private TemplateAdminService service(
        String role,
        DesignTemplateMapper mapper,
        TemplateCategoryMapper categoryMapper,
        TemplateTagMapper tagMapper,
        CapturingAuditLogMapper auditMapper
    ) {
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
            categoryMapper,
            tagMapper,
            new AuditLogService(auditMapper, new ObjectMapper()),
            new ObjectMapper()
        );
    }

    private DesignTemplateMapper.AdminTemplateRow template(long id, String status) {
        DesignTemplateMapper.AdminTemplateRow row = new DesignTemplateMapper.AdminTemplateRow();
        row.setId(id);
        row.setName("朋友圈促销");
        row.setWidth(1080);
        row.setHeight(1440);
        row.setCategoryCode("marketing");
        row.setFeaturedRank(10);
        row.setStatus(status);
        row.setUpdatedAt(Instant.parse("2026-08-19T00:00:00Z"));
        return row;
    }

    private TemplateCategoryMapper.AdminCategoryRow category(long id, String code) {
        TemplateCategoryMapper.AdminCategoryRow row = new TemplateCategoryMapper.AdminCategoryRow();
        row.setId(id);
        row.setCode(code);
        row.setName("营销推广");
        row.setStatus("PUBLISHED");
        return row;
    }

    private TemplateTagMapper.AdminTagRow tag(long id, String code) {
        TemplateTagMapper.AdminTagRow row = new TemplateTagMapper.AdminTagRow();
        row.setId(id);
        row.setCode(code);
        row.setName(code);
        row.setStatus("PUBLISHED");
        return row;
    }

    private static final class CapturingAuditLogMapper implements AuditLogMapper {
        private AuditLogRow row;
        private final List<AuditLogRow> rows = new java.util.ArrayList<>();

        @Override
        public int insert(AuditLogRow row) {
            this.row = row;
            this.rows.add(row);
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
