package com.example.lowcode.home.application;

import com.example.lowcode.audit.application.AuditLogService;
import com.example.lowcode.audit.infrastructure.AuditLogMapper;
import com.example.lowcode.auth.application.AuthRepository;
import com.example.lowcode.auth.security.CurrentUser;
import com.example.lowcode.common.exception.BusinessException;
import com.example.lowcode.template.application.TemplateCoverAdminRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

class HomeTopicAdminServiceTest {
    @Test
    void administratorCreatesTopicWithTemplateAssociationsAndAudit() {
        HomeTopicAdminRepository repository = mock(HomeTopicAdminRepository.class);
        TemplateCoverAdminRepository covers = mock(TemplateCoverAdminRepository.class);
        AuditLogMapper auditMapper = mock(AuditLogMapper.class);
        HomeTopicAdminService service = service("ADMIN", repository, covers, auditMapper);
        when(repository.findByCode("summer-scenes")).thenReturn(Optional.empty());
        when(repository.findExistingTemplateIds(List.of(1001L, 1002L))).thenReturn(List.of(1001L, 1002L));
        when(repository.insert(any())).thenReturn(31L);
        when(repository.findById(31L)).thenReturn(Optional.of(topic(31L, "summer-scenes", "DRAFT", List.of(1001L, 1002L))));
        doAnswer(invocation -> {
            invocation.<AuditLogMapper.AuditLogRow>getArgument(0).setId(1L);
            return 1;
        }).when(auditMapper).insert(any());

        HomeTopicAdminService.TopicView result = service.create(
            new CurrentUser(7, 11),
            new HomeTopicAdminService.TopicCommand(
                "summer-scenes", "夏日场景", "夏季营销模板精选", "EDITORIAL_SCENE", null,
                Instant.parse("2026-06-01T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"), 10,
                List.of(1001L, 1002L)
            )
        );

        assertThat(result.id()).isEqualTo(31L);
        assertThat(result.templateIds()).containsExactly(1001L, 1002L);
        verify(repository).replaceTemplateRelations(31L, List.of(1001L, 1002L));
        verify(auditMapper).insert(any());
    }

    @Test
    void operatorCanListButCannotCreate() {
        HomeTopicAdminRepository repository = mock(HomeTopicAdminRepository.class);
        HomeTopicAdminService service = service("OPERATOR", repository, mock(TemplateCoverAdminRepository.class), mock(AuditLogMapper.class));
        when(repository.findAll(null)).thenReturn(List.of(topic(30L, "summer-promotion", "PUBLISHED", List.of(1001L))));

        assertThat(service.list(new CurrentUser(7, 11), null)).singleElement()
            .extracting(HomeTopicAdminService.TopicView::code).isEqualTo("summer-promotion");
        assertThatThrownBy(() -> service.create(new CurrentUser(7, 11), new HomeTopicAdminService.TopicCommand(
            "new-topic", "新专题", null, "HOTSPOT_CALENDAR", null, null, null, 0, List.of()
        ))).isInstanceOf(BusinessException.class).hasMessageContaining("仅管理员");
    }

    @Test
    void rejectsUnknownTemplateBeforeWriting() {
        HomeTopicAdminRepository repository = mock(HomeTopicAdminRepository.class);
        HomeTopicAdminService service = service("ADMIN", repository, mock(TemplateCoverAdminRepository.class), mock(AuditLogMapper.class));
        when(repository.findByCode("bad-topic")).thenReturn(Optional.empty());
        when(repository.findExistingTemplateIds(List.of(9999L))).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(new CurrentUser(7, 11), new HomeTopicAdminService.TopicCommand(
            "bad-topic", "无效专题", null, "EDITORIAL_SCENE", null, null, null, 0, List.of(9999L)
        ))).isInstanceOf(BusinessException.class).hasMessageContaining("模板");
        verify(repository, never()).insert(any());
    }

    private HomeTopicAdminService service(
        String role,
        HomeTopicAdminRepository repository,
        TemplateCoverAdminRepository covers,
        AuditLogMapper auditMapper
    ) {
        AuthRepository authRepository = new AuthRepository() {
            @Override public Optional<UserIdentity> findByPhone(String phone) { return Optional.empty(); }
            @Override public Optional<UserIdentity> findByUserAndTenant(long userId, long tenantId) {
                return Optional.of(new UserIdentity(userId, tenantId, "13800000000", "ACTIVE", "ACTIVE", role));
            }
            @Override public UserIdentity createUserWithDefaultTenant(String phone) { throw new UnsupportedOperationException(); }
        };
        return new HomeTopicAdminService(
            authRepository, repository, covers, new AuditLogService(auditMapper, new ObjectMapper())
        );
    }

    private HomeTopicAdminRepository.TopicRecord topic(long id, String code, String status, List<Long> templateIds) {
        return new HomeTopicAdminRepository.TopicRecord(
            id, code, "专题标题", "专题副标题", "EDITORIAL_SCENE", null,
            null, null, 10, status, templateIds
        );
    }
}
