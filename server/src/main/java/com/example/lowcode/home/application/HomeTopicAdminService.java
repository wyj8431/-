package com.example.lowcode.home.application;

import com.example.lowcode.audit.application.AuditLogService;
import com.example.lowcode.auth.application.AuthRepository;
import com.example.lowcode.auth.security.CurrentUser;
import com.example.lowcode.common.exception.BusinessException;
import com.example.lowcode.common.exception.ErrorCode;
import com.example.lowcode.template.application.TemplateCoverAdminRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class HomeTopicAdminService {
    private static final Pattern CODE = Pattern.compile("[a-z0-9-]{1,64}");
    private static final Set<String> TYPES = Set.of("HOTSPOT_CALENDAR", "EDITORIAL_SCENE");
    private static final Set<String> STATUSES = Set.of("DRAFT", "PUBLISHED", "DISABLED");

    private final AuthRepository authRepository;
    private final HomeTopicAdminRepository repository;
    private final TemplateCoverAdminRepository coverRepository;
    private final AuditLogService auditLogService;

    public HomeTopicAdminService(
        AuthRepository authRepository,
        HomeTopicAdminRepository repository,
        TemplateCoverAdminRepository coverRepository,
        AuditLogService auditLogService
    ) {
        this.authRepository = authRepository;
        this.repository = repository;
        this.coverRepository = coverRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<TopicView> list(CurrentUser currentUser, String requestedStatus) {
        requireManager(currentUser);
        return repository.findAll(normalizeStatus(requestedStatus, true)).stream().map(this::toView).toList();
    }

    @Transactional
    public TopicView create(CurrentUser currentUser, TopicCommand command) {
        AuthRepository.UserIdentity actor = requireAdministrator(currentUser);
        TopicCommand normalized = normalize(command);
        ensureCodeAvailable(normalized.code(), null);
        validateReferences(normalized);
        long id = repository.insert(toNewTopic(normalized, "DRAFT"));
        repository.replaceTemplateRelations(id, normalized.templateIds());
        TopicView result = repository.findById(id).map(this::toView)
            .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "首页专题创建失败"));
        auditLogService.record(new AuditLogService.AuditEvent(
            actor.userId(), currentUser.tenantId(), "HOME_TOPIC_CREATE", "HOME_TOPIC", Long.toString(id),
            AuditLogService.Outcome.SUCCESS, null, metadata("code", normalized.code(), "type", normalized.type(), "templateIds", normalized.templateIds())
        ));
        return result;
    }

    @Transactional
    public TopicView update(CurrentUser currentUser, long id, TopicCommand command) {
        AuthRepository.UserIdentity actor = requireAdministrator(currentUser);
        TopicView existing = repository.findById(id).map(this::toView)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "首页专题不存在"));
        TopicCommand normalized = normalize(command);
        ensureCodeAvailable(normalized.code(), id);
        validateReferences(normalized);
        if (!repository.update(id, toNewTopic(normalized, existing.status()))) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "首页专题不存在");
        }
        repository.replaceTemplateRelations(id, normalized.templateIds());
        TopicView result = repository.findById(id).map(this::toView)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "首页专题不存在"));
        auditLogService.record(new AuditLogService.AuditEvent(
            actor.userId(), currentUser.tenantId(), "HOME_TOPIC_UPDATE", "HOME_TOPIC", Long.toString(id),
            AuditLogService.Outcome.SUCCESS, null, metadata("code", normalized.code(), "templateIds", normalized.templateIds())
        ));
        return result;
    }

    @Transactional
    public TopicView changeStatus(CurrentUser currentUser, long id, String requestedStatus) {
        AuthRepository.UserIdentity actor = requireAdministrator(currentUser);
        String status = normalizeStatus(requestedStatus, false);
        TopicView existing = repository.findById(id).map(this::toView)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "首页专题不存在"));
        if (!status.equals(existing.status()) && !repository.updateStatus(id, status)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "首页专题不存在");
        }
        if (!status.equals(existing.status())) {
            auditLogService.record(new AuditLogService.AuditEvent(
                actor.userId(), currentUser.tenantId(), "HOME_TOPIC_STATUS_CHANGE", "HOME_TOPIC", Long.toString(id),
                AuditLogService.Outcome.SUCCESS, null, Map.of("fromStatus", existing.status(), "toStatus", status)
            ));
        }
        return new TopicView(existing.id(), existing.code(), existing.title(), existing.subtitle(), existing.type(), existing.coverAssetId(),
            existing.startsAt(), existing.endsAt(), existing.sortOrder(), status, existing.templateIds());
    }

    @Transactional
    public void delete(CurrentUser currentUser, long id) {
        AuthRepository.UserIdentity actor = requireAdministrator(currentUser);
        TopicView existing = repository.findById(id).map(this::toView)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "首页专题不存在"));
        repository.replaceTemplateRelations(id, List.of());
        HomeTopicAdminRepository.TopicRecord record = repository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "首页专题不存在"));
        if (!repository.delete(id)) throw new BusinessException(ErrorCode.NOT_FOUND, "首页专题不存在");
        auditLogService.record(new AuditLogService.AuditEvent(
            actor.userId(), currentUser.tenantId(), "HOME_TOPIC_DELETE", "HOME_TOPIC", Long.toString(id),
            AuditLogService.Outcome.SUCCESS, null, metadata("code", existing.code(), "status", existing.status(), "templateCount", record.templateIds().size())
        ));
    }

    private TopicCommand normalize(TopicCommand command) {
        if (command == null) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "首页专题参数无效");
        String code = command.code() == null ? "" : command.code().trim().toLowerCase(Locale.ROOT);
        if (!CODE.matcher(code).matches()) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "首页专题编码无效");
        String title = command.title() == null ? "" : command.title().trim();
        if (title.isEmpty() || title.length() > 128) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "首页专题标题无效");
        String subtitle = command.subtitle() == null ? null : command.subtitle().trim();
        if (subtitle != null && subtitle.length() > 255) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "首页专题副标题无效");
        String type = command.type() == null ? "" : command.type().trim().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(type)) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "首页专题类型无效");
        if (command.sortOrder() < 0 || command.sortOrder() > 100000) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "首页专题排序无效");
        if (command.startsAt() != null && command.endsAt() != null && !command.endsAt().isAfter(command.startsAt())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "首页专题时间范围无效");
        }
        List<Long> templateIds = command.templateIds() == null ? List.of() : new LinkedHashSet<>(command.templateIds()).stream().toList();
        if (templateIds.stream().anyMatch(id -> id == null || id < 1)) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模板编号无效");
        return new TopicCommand(code, title, subtitle == null || subtitle.isBlank() ? null : subtitle, type, command.coverAssetId(),
            command.startsAt(), command.endsAt(), command.sortOrder(), templateIds);
    }

    private void validateReferences(TopicCommand command) {
        if (command.coverAssetId() != null) {
            TemplateCoverAdminRepository.CoverAssetRecord cover = coverRepository.findById(command.coverAssetId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "模板封面不存在"));
            if (!"PUBLISHED".equals(cover.status())) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "只有已发布封面才能用于首页专题");
        }
        List<Long> existing = repository.findExistingTemplateIds(command.templateIds());
        if (!existing.containsAll(command.templateIds())) throw new BusinessException(ErrorCode.NOT_FOUND, "首页专题包含不存在的模板");
    }

    private void ensureCodeAvailable(String code, Long currentId) {
        repository.findByCode(code).ifPresent(existing -> {
            if (currentId == null || existing.id() != currentId) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "首页专题编码已存在");
        });
    }

    private HomeTopicAdminRepository.NewTopic toNewTopic(TopicCommand command, String status) {
        return new HomeTopicAdminRepository.NewTopic(command.code(), command.title(), command.subtitle(), command.type(), command.coverAssetId(),
            command.startsAt(), command.endsAt(), command.sortOrder(), status);
    }

    private TopicView toView(HomeTopicAdminRepository.TopicRecord record) {
        return new TopicView(record.id(), record.code(), record.title(), record.subtitle(), record.type(), record.coverAssetId(), record.startsAt(),
            record.endsAt(), record.sortOrder(), record.status(), record.templateIds());
    }

    private String normalizeStatus(String value, boolean allowEmpty) {
        if (value == null || value.isBlank()) {
            if (allowEmpty) return null;
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "首页专题状态无效");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(normalized)) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "首页专题状态无效");
        return normalized;
    }

    private AuthRepository.UserIdentity requireManager(CurrentUser currentUser) {
        AuthRepository.UserIdentity actor = authRepository.findByUserAndTenant(currentUser.userId(), currentUser.tenantId())
            .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "登录已失效"));
        if (!"ACTIVE".equals(actor.userStatus()) || !"ACTIVE".equals(actor.tenantStatus())) throw new BusinessException(ErrorCode.FORBIDDEN, "当前账号无效");
        if (!Set.of("ADMIN", "OPERATOR").contains(actor.tenantRole())) throw new BusinessException(ErrorCode.FORBIDDEN, "无管理员权限");
        return actor;
    }

    private AuthRepository.UserIdentity requireAdministrator(CurrentUser currentUser) {
        AuthRepository.UserIdentity actor = requireManager(currentUser);
        if (!"ADMIN".equals(actor.tenantRole())) throw new BusinessException(ErrorCode.FORBIDDEN, "仅管理员可管理首页专题");
        return actor;
    }

    private Map<String, Object> metadata(Object... values) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) result.put((String) values[i], values[i + 1]);
        return result;
    }

    public record TopicCommand(
        String code,
        String title,
        String subtitle,
        String type,
        Long coverAssetId,
        Instant startsAt,
        Instant endsAt,
        int sortOrder,
        List<Long> templateIds
    ) {
    }

    public record TopicView(
        long id,
        String code,
        String title,
        String subtitle,
        String type,
        Long coverAssetId,
        Instant startsAt,
        Instant endsAt,
        int sortOrder,
        String status,
        List<Long> templateIds
    ) {
    }
}
