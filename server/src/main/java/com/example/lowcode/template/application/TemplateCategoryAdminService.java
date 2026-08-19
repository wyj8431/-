package com.example.lowcode.template.application;

import com.example.lowcode.audit.application.AuditLogService;
import com.example.lowcode.auth.application.AuthRepository;
import com.example.lowcode.auth.security.CurrentUser;
import com.example.lowcode.common.exception.BusinessException;
import com.example.lowcode.common.exception.ErrorCode;
import com.example.lowcode.template.infrastructure.TemplateCategoryMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class TemplateCategoryAdminService {
    private static final Set<String> CATEGORY_STATUSES = Set.of("DRAFT", "PUBLISHED", "DISABLED");

    private final AuthRepository authRepository;
    private final TemplateCategoryMapper categoryMapper;
    private final AuditLogService auditLogService;

    public TemplateCategoryAdminService(
        AuthRepository authRepository,
        TemplateCategoryMapper categoryMapper,
        AuditLogService auditLogService
    ) {
        this.authRepository = authRepository;
        this.categoryMapper = categoryMapper;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<CategoryView> list(CurrentUser currentUser, String requestedStatus) {
        requireManager(currentUser);
        String status = normalizeStatus(requestedStatus, true);
        return categoryMapper.findAll(status).stream()
            .map(row -> new CategoryView(
                row.getId(), row.getCode(), row.getName(), row.getParentCode(), row.getSortOrder(), row.getStatus()
            ))
            .toList();
    }

    @Transactional
    public CategoryView changeStatus(CurrentUser currentUser, String code, String requestedStatus) {
        AuthRepository.UserIdentity actor = requireAdministrator(currentUser);
        String normalizedCode = normalizeCode(code);
        String status = normalizeStatus(requestedStatus, false);
        TemplateCategoryMapper.AdminCategoryRow category = categoryMapper.findAll(null).stream()
            .filter(row -> normalizedCode.equals(row.getCode()))
            .findFirst()
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "模板分类不存在"));
        if (!status.equals(category.getStatus()) && categoryMapper.updateStatus(normalizedCode, status) != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "模板分类不存在");
        }
        if (!status.equals(category.getStatus())) {
            auditLogService.record(new AuditLogService.AuditEvent(
                actor.userId(), currentUser.tenantId(), "TEMPLATE_CATEGORY_STATUS_CHANGE", "TEMPLATE_CATEGORY",
                Long.toString(category.getId()), AuditLogService.Outcome.SUCCESS, null,
                Map.of("code", normalizedCode, "fromStatus", category.getStatus(), "toStatus", status)
            ));
        }
        return new CategoryView(
            category.getId(), category.getCode(), category.getName(), category.getParentCode(), category.getSortOrder(), status
        );
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
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅管理员可修改模板分类状态");
        }
        return actor;
    }

    private String normalizeStatus(String value, boolean allowEmpty) {
        if (value == null || value.isBlank()) {
            if (allowEmpty) return null;
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模板分类状态无效");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!CATEGORY_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模板分类状态无效");
        }
        return normalized;
    }

    private String normalizeCode(String value) {
        if (value == null || !value.matches("[a-z0-9-]{1,64}")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模板分类编码无效");
        }
        return value;
    }

    public record CategoryView(long id, String code, String name, String parentCode, int sortOrder, String status) {
    }
}
