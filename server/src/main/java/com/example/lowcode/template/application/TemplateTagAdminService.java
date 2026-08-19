package com.example.lowcode.template.application;

import com.example.lowcode.audit.application.AuditLogService;
import com.example.lowcode.auth.application.AuthRepository;
import com.example.lowcode.auth.security.CurrentUser;
import com.example.lowcode.common.exception.BusinessException;
import com.example.lowcode.common.exception.ErrorCode;
import com.example.lowcode.template.infrastructure.TemplateTagMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class TemplateTagAdminService {
    private static final Set<String> TAG_STATUSES = Set.of("DRAFT", "PUBLISHED", "DISABLED");

    private final AuthRepository authRepository;
    private final TemplateTagMapper tagMapper;
    private final AuditLogService auditLogService;

    public TemplateTagAdminService(
        AuthRepository authRepository,
        TemplateTagMapper tagMapper,
        AuditLogService auditLogService
    ) {
        this.authRepository = authRepository;
        this.tagMapper = tagMapper;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<TagView> list(CurrentUser currentUser, String requestedStatus) {
        requireManager(currentUser);
        String status = normalizeStatus(requestedStatus, true);
        return tagMapper.findAll(status).stream()
            .map(row -> new TagView(row.getId(), row.getCode(), row.getName(), row.getSortOrder(), row.getStatus()))
            .toList();
    }

    @Transactional
    public TagView changeStatus(CurrentUser currentUser, String code, String requestedStatus) {
        AuthRepository.UserIdentity actor = requireAdministrator(currentUser);
        String normalizedCode = normalizeCode(code);
        String status = normalizeStatus(requestedStatus, false);
        TemplateTagMapper.AdminTagRow tag = tagMapper.findByCode(normalizedCode);
        if (tag == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "标签不存在");
        }
        if (status.equals(tag.getStatus())) {
            return toView(tag);
        }
        if (tagMapper.updateStatus(normalizedCode, status) != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "标签不存在");
        }
        auditLogService.record(new AuditLogService.AuditEvent(
            actor.userId(), currentUser.tenantId(), "TEMPLATE_TAG_STATUS_CHANGE", "TEMPLATE_TAG",
            Long.toString(tag.getId()), AuditLogService.Outcome.SUCCESS, null,
            Map.of("code", normalizedCode, "fromStatus", tag.getStatus(), "toStatus", status)
        ));
        tag.setStatus(status);
        return toView(tag);
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
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅管理员可修改模板标签状态");
        }
        return actor;
    }

    private String normalizeStatus(String value, boolean allowEmpty) {
        if (value == null || value.isBlank()) {
            if (allowEmpty) return null;
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模板标签状态无效");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!TAG_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模板标签状态无效");
        }
        return normalized;
    }

    private String normalizeCode(String value) {
        if (value == null || !value.matches("[a-z0-9-]{1,64}")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模板标签编码无效");
        }
        return value;
    }

    private TagView toView(TemplateTagMapper.AdminTagRow row) {
        return new TagView(row.getId(), row.getCode(), row.getName(), row.getSortOrder(), row.getStatus());
    }

    public record TagView(long id, String code, String name, int sortOrder, String status) {
    }
}
