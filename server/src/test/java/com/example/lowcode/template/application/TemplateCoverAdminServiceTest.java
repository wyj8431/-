package com.example.lowcode.template.application;

import com.example.lowcode.asset.application.StorageGateway;
import com.example.lowcode.asset.domain.ImageInspector;
import com.example.lowcode.audit.application.AuditLogService;
import com.example.lowcode.audit.infrastructure.AuditLogMapper;
import com.example.lowcode.auth.application.AuthRepository;
import com.example.lowcode.auth.security.CurrentUser;
import com.example.lowcode.common.exception.BusinessException;
import com.example.lowcode.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TemplateCoverAdminServiceTest {
    private static final CurrentUser ADMIN = new CurrentUser(7, 11);
    private static final CurrentUser OPERATOR = new CurrentUser(8, 11);
    private static final Instant NOW = Instant.parse("2026-08-19T04:00:00Z");

    @Test
    void administratorPresignsAndCompletesPlatformCoverWithVerifiedImageMetadata() {
        TemplateCoverAdminRepository repository = mock(TemplateCoverAdminRepository.class);
        StorageGateway storage = mock(StorageGateway.class);
        ImageInspector inspector = mock(ImageInspector.class);
        CapturingAuditMapper auditMapper = new CapturingAuditMapper();
        when(repository.createUploadSession(any())).thenReturn(3001L);
        when(storage.presignPut(any(), eq("image/png"), any(Duration.class))).thenReturn("https://storage.test/upload");
        when(repository.findPendingSession(ADMIN.tenantId(), ADMIN.userId(), 3001L)).thenReturn(Optional.of(
            new TemplateCoverAdminRepository.UploadSession(
                3001L, ADMIN.tenantId(), ADMIN.userId(), "cover.png", "image/png", 4L,
                "9f64a747e1b97f131fabb6b447296c9b6f0201e79fb3c5356e6c77e89b6a806a",
                "tenant/11/template-cover/upload/3001.png", NOW.plusSeconds(600)
            )
        ));
        when(storage.readBounded(any(), any(Long.class))).thenReturn(new byte[] {1, 2, 3, 4});
        when(inspector.inspect(any())).thenReturn(new ImageInspector.ImageInfo("image/png", 1080, 1440));
        when(repository.markCompleted(3001L, NOW)).thenReturn(true);
        when(repository.createAsset(any())).thenReturn(4001L);

        TemplateCoverAdminService service = service(repository, storage, inspector, auditMapper);
        TemplateCoverAdminService.PresignResult presign = service.presign(
            ADMIN,
            new TemplateCoverAdminService.PresignCommand(
                " cover.png ", "image/png", 4L,
                "9f64a747e1b97f131fabb6b447296c9b6f0201e79fb3c5356e6c77e89b6a806a"
            )
        );
        TemplateCoverAdminService.CoverView cover = service.complete(
            ADMIN, new TemplateCoverAdminService.CompleteCommand(3001L)
        );

        assertThat(presign.sessionId()).isEqualTo(3001L);
        assertThat(presign.uploadUrl()).isEqualTo("https://storage.test/upload");
        assertThat(cover.id()).isEqualTo(4001L);
        assertThat(cover.status()).isEqualTo("DRAFT");
        assertThat(cover.width()).isEqualTo(1080);
        verify(repository).createAsset(any(TemplateCoverAdminRepository.NewAsset.class));
        assertThat(auditMapper.actions()).contains("TEMPLATE_COVER_ASSET_CREATE");
    }

    @Test
    void operatorCanReadButCannotMutateCoverResources() {
        TemplateCoverAdminRepository repository = mock(TemplateCoverAdminRepository.class);
        when(repository.findAll(null)).thenReturn(List.of(cover(4001L, "DRAFT")));
        TemplateCoverAdminService service = service(repository, mock(StorageGateway.class), mock(ImageInspector.class));

        assertThat(service.list(OPERATOR, null)).singleElement().extracting(TemplateCoverAdminService.CoverView::status)
            .isEqualTo("DRAFT");
        assertThatThrownBy(() -> service.presign(OPERATOR, new TemplateCoverAdminService.PresignCommand("a.png", "image/png", 1, "a".repeat(64))))
            .isInstanceOf(BusinessException.class).hasMessageContaining("仅管理员");
        assertThatThrownBy(() -> service.changeStatus(OPERATOR, 4001L, "PUBLISHED"))
            .isInstanceOf(BusinessException.class).hasMessageContaining("仅管理员");
        assertThatThrownBy(() -> service.delete(OPERATOR, 4001L))
            .isInstanceOf(BusinessException.class).hasMessageContaining("仅管理员");
        verify(repository, never()).updateStatus(any(Long.class), any());
    }

    @Test
    void administratorChangesCoverStatusAndAuditsIt() {
        TemplateCoverAdminRepository repository = mock(TemplateCoverAdminRepository.class);
        when(repository.findById(4001L)).thenReturn(Optional.of(cover(4001L, "DRAFT")));
        when(repository.updateStatus(4001L, "PUBLISHED")).thenReturn(true);
        CapturingAuditMapper auditMapper = new CapturingAuditMapper();
        TemplateCoverAdminService service = service(repository, mock(StorageGateway.class), mock(ImageInspector.class), auditMapper);

        TemplateCoverAdminService.CoverView result = service.changeStatus(ADMIN, 4001L, "published");

        assertThat(result.status()).isEqualTo("PUBLISHED");
        assertThat(auditMapper.actions()).contains("TEMPLATE_COVER_ASSET_STATUS_CHANGE");
    }

    @Test
    void refusesToDeleteReferencedCoverWithoutRemovingObject() {
        TemplateCoverAdminRepository repository = mock(TemplateCoverAdminRepository.class);
        when(repository.findById(4001L)).thenReturn(Optional.of(cover(4001L, "DRAFT")));
        when(repository.countReferences(4001L)).thenReturn(1);
        StorageGateway storage = mock(StorageGateway.class);
        TemplateCoverAdminService service = service(repository, storage, mock(ImageInspector.class));

        assertThatThrownBy(() -> service.delete(ADMIN, 4001L))
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).errorCode())
            .isEqualTo(ErrorCode.TEMPLATE_COVER_IN_USE);
        verify(repository, never()).deleteById(4001L);
        verify(storage, never()).delete(any());
    }

    @Test
    void bindsAndUnbindsCoverOnTemplateWithAudit() {
        TemplateCoverAdminRepository repository = mock(TemplateCoverAdminRepository.class);
        when(repository.findById(4001L)).thenReturn(Optional.of(cover(4001L, "PUBLISHED")));
        when(repository.findTemplateCoverId(1001L)).thenReturn(Optional.of(2001L));
        when(repository.updateTemplateCover(1001L, null)).thenReturn(true);
        CapturingAuditMapper auditMapper = new CapturingAuditMapper();
        TemplateCoverAdminService service = service(repository, mock(StorageGateway.class), mock(ImageInspector.class), auditMapper);

        assertThat(service.bindTemplateCover(ADMIN, 1001L, null).coverAssetId()).isNull();
        verify(repository).updateTemplateCover(1001L, null);
        assertThat(auditMapper.actions()).contains("TEMPLATE_COVER_BIND");
    }

    private TemplateCoverAdminService service(
        TemplateCoverAdminRepository repository,
        StorageGateway storage,
        ImageInspector inspector
    ) {
        return service(repository, storage, inspector, new CapturingAuditMapper());
    }

    private TemplateCoverAdminService service(
        TemplateCoverAdminRepository repository,
        StorageGateway storage,
        ImageInspector inspector,
        AuditLogMapper auditMapper
    ) {
        AuthRepository authRepository = new AuthRepository() {
            @Override public Optional<UserIdentity> findByPhone(String phone) { return Optional.empty(); }
            @Override public Optional<UserIdentity> findByUserAndTenant(long userId, long tenantId) {
                String role = userId == OPERATOR.userId() ? "OPERATOR" : "ADMIN";
                return Optional.of(new UserIdentity(userId, tenantId, "13800000000", "ACTIVE", "ACTIVE", role));
            }
            @Override public UserIdentity createUserWithDefaultTenant(String phone) { throw new UnsupportedOperationException(); }
        };
        return new TemplateCoverAdminService(
            authRepository, repository, storage, inspector,
            new AuditLogService(auditMapper, new ObjectMapper()),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private TemplateCoverAdminRepository.CoverAssetRecord cover(long id, String status) {
        return new TemplateCoverAdminRepository.CoverAssetRecord(
            id, "platform/cover/" + id + ".png", "image/png", 4L,
            "a".repeat(64), 1080, 1440, status, NOW, NOW
        );
    }

    private static class CapturingAuditMapper implements AuditLogMapper {
        private final java.util.ArrayList<String> actionList = new java.util.ArrayList<>();
        List<String> actions() { return actionList; }
        @Override public int insert(AuditLogRow row) { actionList.add(row.action()); row.setId(1L); return 1; }
        @Override public List<AuditLogViewRow> findPage(long tenantId, String action, String outcome, java.sql.Timestamp from, java.sql.Timestamp to, int pageSize, long offset) { return List.of(); }
        @Override public long countPage(long tenantId, String action, String outcome, java.sql.Timestamp from, java.sql.Timestamp to) { return 0; }
    }

}
