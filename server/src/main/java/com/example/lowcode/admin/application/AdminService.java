package com.example.lowcode.admin.application;

import com.example.lowcode.auth.application.AuthRepository;
import com.example.lowcode.auth.application.RefreshTokenRepository;
import com.example.lowcode.auth.security.CurrentUser;
import com.example.lowcode.auth.infrastructure.TenantMemberMapper;
import com.example.lowcode.auth.infrastructure.UserMapper;
import com.example.lowcode.audit.application.AuditLogService;
import com.example.lowcode.admin.infrastructure.AdminMapper;
import com.example.lowcode.common.exception.BusinessException;
import com.example.lowcode.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class AdminService {
    private final AuthRepository authRepository;
    private final AdminMapper adminMapper;
    private final TenantMemberMapper tenantMemberMapper;
    private final UserMapper userMapper;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditLogService auditLogService;

    public AdminService(
        AuthRepository authRepository,
        AdminMapper adminMapper,
        TenantMemberMapper tenantMemberMapper,
        UserMapper userMapper,
        RefreshTokenRepository refreshTokenRepository,
        AuditLogService auditLogService
    ) {
        this.authRepository = authRepository;
        this.adminMapper = adminMapper;
        this.tenantMemberMapper = tenantMemberMapper;
        this.userMapper = userMapper;
        this.refreshTokenRepository = refreshTokenRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public Summary summary(CurrentUser currentUser) {
        requireManager(currentUser);
        AdminMapper.SummaryRow row = adminMapper.findSummary(currentUser.tenantId());
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "团队不存在");
        }
        return new Summary(row.getMemberCount(), row.getActiveUserCount(), row.getAuditCount(), "UP");
    }

    @Transactional(readOnly = true)
    public MemberPage members(CurrentUser currentUser, MemberQuery query) {
        requireManager(currentUser);
        MemberQuery normalized = normalize(query);
        long offset = (long) (normalized.page() - 1) * normalized.pageSize();
        List<MemberView> items = adminMapper.findMembers(
                currentUser.tenantId(), normalized.role(), normalized.status(), normalized.pageSize(), offset
            ).stream()
            .map(this::toMemberView)
            .toList();
        return new MemberPage(
            items,
            normalized.page(),
            normalized.pageSize(),
            adminMapper.countMembers(currentUser.tenantId(), normalized.role(), normalized.status())
        );
    }

    @Transactional(readOnly = true)
    public AuditLogService.AuditPage auditLogs(CurrentUser currentUser, AuditLogService.AuditQuery query) {
        requireManager(currentUser);
        return auditLogService.page(currentUser.tenantId(), query);
    }

    @Transactional(readOnly = true)
    public AuditLogService.AuditExport auditLogExport(
        CurrentUser currentUser,
        AuditLogService.AuditExportQuery query
    ) {
        requireManager(currentUser);
        return auditLogService.export(currentUser.tenantId(), query);
    }

    @Transactional
    public RoleChangeResult changeTenantRole(CurrentUser currentUser, long targetUserId, String requestedRole) {
        AuthRepository.UserIdentity actor = requireAdministrator(currentUser);
        String role = requestedRole == null ? "" : requestedRole.trim().toUpperCase();
        if (!Set.of("ADMIN", "USER", "OPERATOR").contains(role)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "成员角色无效");
        }
        AuthRepository.UserIdentity target = authRepository
            .findByUserAndTenant(targetUserId, currentUser.tenantId())
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "团队成员不存在"));
        if (!"ACTIVE".equals(target.userStatus()) || !"ACTIVE".equals(target.tenantStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "成员账号当前无效");
        }
        String currentRole = tenantMemberMapper.findRoleForUpdate(currentUser.tenantId(), targetUserId);
        if (currentRole == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "团队成员不存在");
        }
        if ("ADMIN".equals(currentRole) && !"ADMIN".equals(role)
            && tenantMemberMapper.findAdminUserIdsForUpdate(currentUser.tenantId()).size() <= 1) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "至少保留一名管理员");
        }
        if (!currentRole.equals(role)) {
            if (tenantMemberMapper.updateRole(currentUser.tenantId(), targetUserId, role) != 1) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "团队成员不存在");
            }
            userMapper.incrementSecurityVersion(targetUserId);
            refreshTokenRepository.revokeUser(targetUserId, java.time.Instant.now(), "ROLE_CHANGE");
            auditLogService.record(new AuditLogService.AuditEvent(
                actor.userId(), currentUser.tenantId(), "ROLE_CHANGE", "TENANT_MEMBER",
                Long.toString(targetUserId), AuditLogService.Outcome.SUCCESS, null,
                Map.of("fromRole", currentRole, "toRole", role)
            ));
        }
        return new RoleChangeResult(targetUserId, currentUser.tenantId(), role);
    }

    private AuthRepository.UserIdentity requireManager(CurrentUser currentUser) {
        AuthRepository.UserIdentity actor = authRepository
            .findByUserAndTenant(currentUser.userId(), currentUser.tenantId())
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
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅管理员可修改成员角色");
        }
        return actor;
    }

    private MemberQuery normalize(MemberQuery query) {
        if (query == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "查询条件无效");
        }
        if (query.page() < 1 || query.page() > 100_000) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "页码无效");
        }
        if (query.pageSize() < 1 || query.pageSize() > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "每页数量无效");
        }
        String role = normalizeFilter(query.role(), Set.of("ADMIN", "USER", "OPERATOR"), "成员角色");
        String status = normalizeFilter(query.status(), Set.of("ACTIVE", "DISABLED"), "成员状态");
        return new MemberQuery(query.page(), query.pageSize(), role, status);
    }

    private String normalizeFilter(String value, Set<String> allowed, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, label + "无效");
        }
        return normalized;
    }

    private MemberView toMemberView(AdminMapper.MemberRow row) {
        Timestamp joinedAt = row.getJoinedAt();
        return new MemberView(
            row.getUserId(),
            maskPhone(row.getPhone()),
            row.getTenantRole(),
            row.getUserStatus(),
            joinedAt == null ? null : joinedAt.toInstant()
        );
    }

    static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return "****";
        }
        if (phone.length() <= 7) {
            return "****" + phone.substring(Math.max(0, phone.length() - 2));
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    public record Summary(
        long memberCount,
        long activeUserCount,
        long auditCount,
        String health
    ) {
    }

    public record RoleChangeResult(long userId, long tenantId, String tenantRole) {
    }

    public record MemberQuery(int page, int pageSize, String role, String status) {
    }

    public record MemberPage(List<MemberView> items, int page, int pageSize, long total) {
        public MemberPage {
            items = List.copyOf(items);
        }
    }

    public record MemberView(
        long userId,
        String phoneMasked,
        String tenantRole,
        String userStatus,
        Instant joinedAt
    ) {
    }
}
