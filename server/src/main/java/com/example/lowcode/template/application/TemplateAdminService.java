package com.example.lowcode.template.application;

import com.example.lowcode.audit.application.AuditLogService;
import com.example.lowcode.auth.application.AuthRepository;
import com.example.lowcode.auth.security.CurrentUser;
import com.example.lowcode.common.exception.BusinessException;
import com.example.lowcode.common.exception.ErrorCode;
import com.example.lowcode.template.infrastructure.DesignTemplateMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class TemplateAdminService {
    private static final Set<String> TEMPLATE_STATUSES = Set.of("DRAFT", "PUBLISHED", "DISABLED");

    private final AuthRepository authRepository;
    private final DesignTemplateMapper templateMapper;
    private final AuditLogService auditLogService;

    public TemplateAdminService(
        AuthRepository authRepository,
        DesignTemplateMapper templateMapper,
        AuditLogService auditLogService
    ) {
        this.authRepository = authRepository;
        this.templateMapper = templateMapper;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<TemplateView> list(CurrentUser currentUser, String requestedStatus) {
        requireManager(currentUser);
        String status = normalizeStatus(requestedStatus, true);
        return templateMapper.findAdmin(status).stream().map(this::toView).toList();
    }

    @Transactional
    public TemplateView changeStatus(CurrentUser currentUser, long templateId, String requestedStatus) {
        AuthRepository.UserIdentity actor = requireAdministrator(currentUser);
        if (templateId <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模板编号无效");
        }
        String status = normalizeStatus(requestedStatus, false);
        DesignTemplateMapper.AdminTemplateRow template = templateMapper.findAdminById(templateId);
        if (template == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "模板不存在");
        }
        if (status.equals(template.getStatus())) {
            return toView(template);
        }

        Instant publishedAt = "PUBLISHED".equals(status) ? Instant.now() : null;
        if (templateMapper.updateStatus(templateId, status, publishedAt) != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "模板不存在");
        }
        auditLogService.record(new AuditLogService.AuditEvent(
            actor.userId(), currentUser.tenantId(), "TEMPLATE_STATUS_CHANGE", "DESIGN_TEMPLATE",
            Long.toString(templateId), AuditLogService.Outcome.SUCCESS, null,
            Map.of("fromStatus", template.getStatus(), "toStatus", status)
        ));
        template.setStatus(status);
        template.setPublishedAt(publishedAt);
        template.setUpdatedAt(Instant.now());
        return toView(template);
    }

    private AuthRepository.UserIdentity requireManager(CurrentUser currentUser) {
        AuthRepository.UserIdentity actor = authRepository.findByUserAndTenant(currentUser.userId(), currentUser.tenantId())
            .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "登录已失效"));
        if (!"ACTIVE".equals(actor.userStatus()) || !"ACTIVE".equals(actor.tenantStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前账号无效");
        }
        if (!"ADMIN".equals(actor.tenantRole()) && !"OPERATOR".equals(actor.tenantRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无管理员权限");
        }
        return actor;
    }

    private AuthRepository.UserIdentity requireAdministrator(CurrentUser currentUser) {
        AuthRepository.UserIdentity actor = requireManager(currentUser);
        if (!"ADMIN".equals(actor.tenantRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅管理员可修改模板状态");
        }
        return actor;
    }

    private String normalizeStatus(String value, boolean allowEmpty) {
        if (value == null || value.isBlank()) {
            if (allowEmpty) return null;
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模板状态无效");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!TEMPLATE_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模板状态无效");
        }
        return normalized;
    }

    private TemplateView toView(DesignTemplateMapper.AdminTemplateRow row) {
        return new TemplateView(
            row.getId(), row.getName(), row.getWidth(), row.getHeight(), row.getCategoryCode(),
            row.getCoverAssetId(), row.getFeaturedRank(), row.getStatus(), row.getPublishedAt(), row.getUpdatedAt()
        );
    }

    public record TemplateView(
        long id,
        String name,
        int width,
        int height,
        String categoryCode,
        Long coverAssetId,
        Integer featuredRank,
        String status,
        Instant publishedAt,
        Instant updatedAt
    ) {
    }
}
