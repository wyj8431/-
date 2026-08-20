package com.example.lowcode.template.application;

import com.example.lowcode.audit.application.AuditLogService;
import com.example.lowcode.auth.application.AuthRepository;
import com.example.lowcode.auth.security.CurrentUser;
import com.example.lowcode.common.exception.BusinessException;
import com.example.lowcode.common.exception.ErrorCode;
import com.example.lowcode.template.infrastructure.DesignTemplateMapper;
import com.example.lowcode.template.infrastructure.TemplateCategoryMapper;
import com.example.lowcode.template.infrastructure.TemplateTagMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class TemplateAdminService {
    private static final Set<String> TEMPLATE_STATUSES = Set.of("DRAFT", "PUBLISHED", "DISABLED");
    private static final Pattern PUBLIC_CODE = Pattern.compile("[a-z0-9-]{1,64}");

    private final AuthRepository authRepository;
    private final DesignTemplateMapper templateMapper;
    private final TemplateCategoryMapper categoryMapper;
    private final TemplateTagMapper tagMapper;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public TemplateAdminService(
        AuthRepository authRepository,
        DesignTemplateMapper templateMapper,
        TemplateCategoryMapper categoryMapper,
        TemplateTagMapper tagMapper,
        AuditLogService auditLogService,
        ObjectMapper objectMapper
    ) {
        this.authRepository = authRepository;
        this.templateMapper = templateMapper;
        this.categoryMapper = categoryMapper;
        this.tagMapper = tagMapper;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<TemplateView> list(CurrentUser currentUser, String requestedStatus) {
        requireManager(currentUser);
        String status = normalizeStatus(requestedStatus, true);
        List<DesignTemplateMapper.AdminTemplateRow> rows = templateMapper.findAdmin(status);
        Map<Long, List<String>> tagCodes = tagCodesByTemplate(rows);
        return rows.stream().map(row -> toView(row, tagCodes.getOrDefault(row.getId(), List.of()))).toList();
    }

    @Transactional
    public TemplateView create(
        CurrentUser currentUser,
        String name,
        int width,
        int height,
        String categoryCode,
        List<String> tagCodes,
        Integer featuredRank
    ) {
        AuthRepository.UserIdentity actor = requireAdministrator(currentUser);
        String normalizedName = normalizeName(name);
        validateDimensions(width, height);
        String normalizedCategoryCode = normalizeOptionalCode(categoryCode, "模板分类编码");
        Integer normalizedFeaturedRank = normalizeFeaturedRank(featuredRank);
        TemplateCategoryMapper.AdminCategoryRow category = resolveCategory(normalizedCategoryCode);
        List<String> normalizedTagCodes = normalizeTagCodes(tagCodes);
        Map<String, TemplateTagMapper.AdminTagRow> tags = resolveTags(normalizedTagCodes);

        DesignTemplateMapper.AdminTemplateRow template = new DesignTemplateMapper.AdminTemplateRow();
        template.setName(normalizedName);
        template.setWidth(width);
        template.setHeight(height);
        template.setCategoryId(category == null ? null : category.getId());
        template.setCategoryCode(category == null ? null : category.getCode());
        template.setFeaturedRank(normalizedFeaturedRank);
        template.setStatus("DRAFT");
        template.setSchemaJson(starterSchema(width, height));
        if (templateMapper.insert(template) != 1 || template.getId() <= 0) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "模板创建失败");
        }

        List<Long> tagIds = normalizedTagCodes.stream().map(code -> tags.get(code).getId()).toList();
        insertRelations(template.getId(), tagIds);
        auditLogService.record(new AuditLogService.AuditEvent(
            actor.userId(), currentUser.tenantId(), "TEMPLATE_CREATE", "DESIGN_TEMPLATE",
            Long.toString(template.getId()), AuditLogService.Outcome.SUCCESS, null,
            metadata("name", normalizedName, "width", width, "height", height,
                "categoryCode", normalizedCategoryCode, "featuredRank", normalizedFeaturedRank,
                "tagCodes", normalizedTagCodes, "status", "DRAFT")
        ));
        return toView(template, normalizedTagCodes);
    }

    @Transactional
    public TemplateView update(
        CurrentUser currentUser,
        long templateId,
        String name,
        int width,
        int height,
        String categoryCode,
        List<String> tagCodes,
        Integer featuredRank
    ) {
        AuthRepository.UserIdentity actor = requireAdministrator(currentUser);
        if (templateId <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模板编号无效");
        }
        DesignTemplateMapper.AdminTemplateRow template = templateMapper.findAdminById(templateId);
        if (template == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "模板不存在");
        }
        String normalizedName = normalizeName(name);
        validateDimensions(width, height);
        String normalizedCategoryCode = normalizeOptionalCode(categoryCode, "模板分类编码");
        Integer normalizedFeaturedRank = normalizeFeaturedRank(featuredRank);
        TemplateCategoryMapper.AdminCategoryRow category = resolveCategory(normalizedCategoryCode);
        List<String> normalizedTagCodes = normalizeTagCodes(tagCodes);
        Map<String, TemplateTagMapper.AdminTagRow> tags = resolveTags(normalizedTagCodes);
        List<String> currentTagCodes = currentTagCodes(templateId);
        boolean detailsChanged = !normalizedName.equals(template.getName())
            || width != template.getWidth()
            || height != template.getHeight()
            || !sameNullable(normalizedCategoryCode, template.getCategoryCode())
            || !sameNullable(normalizedFeaturedRank, template.getFeaturedRank());
        boolean tagsChanged = !new LinkedHashSet<>(currentTagCodes).equals(new LinkedHashSet<>(normalizedTagCodes));

        if (!detailsChanged && !tagsChanged) {
            return toView(template, currentTagCodes);
        }
        if (detailsChanged && templateMapper.updateDetails(
            templateId, normalizedName, width, height,
            category == null ? null : category.getId(), template.getCoverAssetId(), normalizedFeaturedRank
        ) != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "模板不存在");
        }
        if (tagsChanged) {
            tagMapper.deleteRelations(templateId);
            List<Long> tagIds = normalizedTagCodes.stream().map(code -> tags.get(code).getId()).toList();
            insertRelations(templateId, tagIds);
            auditLogService.record(new AuditLogService.AuditEvent(
                actor.userId(), currentUser.tenantId(), "TEMPLATE_TAG_ASSOCIATIONS_REPLACE", "DESIGN_TEMPLATE",
                Long.toString(templateId), AuditLogService.Outcome.SUCCESS, null,
                metadata("fromTagCodes", currentTagCodes, "toTagCodes", normalizedTagCodes)
            ));
        }
        if (detailsChanged) {
            auditLogService.record(new AuditLogService.AuditEvent(
                actor.userId(), currentUser.tenantId(), "TEMPLATE_UPDATE", "DESIGN_TEMPLATE",
                Long.toString(templateId), AuditLogService.Outcome.SUCCESS, null,
                metadata("fromName", template.getName(), "toName", normalizedName,
                    "fromWidth", template.getWidth(), "toWidth", width,
                    "fromHeight", template.getHeight(), "toHeight", height,
                    "fromCategoryCode", template.getCategoryCode(), "toCategoryCode", normalizedCategoryCode,
                    "fromFeaturedRank", template.getFeaturedRank(), "toFeaturedRank", normalizedFeaturedRank)
            ));
        }
        template.setName(normalizedName);
        template.setWidth(width);
        template.setHeight(height);
        template.setCategoryId(category == null ? null : category.getId());
        template.setCategoryCode(normalizedCategoryCode);
        template.setFeaturedRank(normalizedFeaturedRank);
        template.setUpdatedAt(Instant.now());
        return toView(template, normalizedTagCodes);
    }

    @Transactional
    public void delete(CurrentUser currentUser, long templateId) {
        AuthRepository.UserIdentity actor = requireAdministrator(currentUser);
        if (templateId <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模板编号无效");
        }
        DesignTemplateMapper.AdminTemplateRow template = templateMapper.findAdminById(templateId);
        if (template == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "模板不存在");
        }
        if (templateMapper.countReferences(templateId) > 0) {
            throw new BusinessException(ErrorCode.TEMPLATE_IN_USE);
        }
        if (templateMapper.deleteById(templateId) != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "模板不存在");
        }
        auditLogService.record(new AuditLogService.AuditEvent(
            actor.userId(), currentUser.tenantId(), "TEMPLATE_DELETE", "DESIGN_TEMPLATE",
            Long.toString(templateId), AuditLogService.Outcome.SUCCESS, null,
            metadata("name", template.getName(), "width", template.getWidth(), "height", template.getHeight(),
                "categoryCode", template.getCategoryCode(), "status", template.getStatus())
        ));
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
            return toView(template, currentTagCodes(templateId));
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
        return toView(template, currentTagCodes(templateId));
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
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅管理员可管理模板");
        }
        return actor;
    }

    private String normalizeName(String value) {
        if (value == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模板名称无效");
        }
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > 128) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模板名称无效");
        }
        return normalized;
    }

    private void validateDimensions(int width, int height) {
        if (width < 1 || width > 10_000 || height < 1 || height > 10_000) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模板尺寸无效");
        }
    }

    private String normalizeOptionalCode(String value, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (!PUBLIC_CODE.matcher(normalized).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, label + "无效");
        }
        return normalized;
    }

    private Integer normalizeFeaturedRank(Integer value) {
        if (value == null) {
            return null;
        }
        if (value < 0 || value > 100_000) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模板推荐位无效");
        }
        return value;
    }

    private TemplateCategoryMapper.AdminCategoryRow resolveCategory(String code) {
        if (code == null) {
            return null;
        }
        TemplateCategoryMapper.AdminCategoryRow category = categoryMapper.findByCode(code);
        if (category == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模板分类不存在");
        }
        return category;
    }

    private List<String> normalizeTagCodes(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || !PUBLIC_CODE.matcher(value.trim()).matches()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模板标签编码无效");
            }
            normalized.add(value.trim());
        }
        if (normalized.size() > 32) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模板标签数量过多");
        }
        return List.copyOf(normalized);
    }

    private Map<String, TemplateTagMapper.AdminTagRow> resolveTags(List<String> codes) {
        if (codes.isEmpty()) {
            return Map.of();
        }
        List<TemplateTagMapper.AdminTagRow> rows = tagMapper.findAdminByCodes(codes);
        if (rows == null || rows.size() != codes.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模板标签不存在");
        }
        Map<String, TemplateTagMapper.AdminTagRow> byCode = rows.stream()
            .collect(Collectors.toMap(TemplateTagMapper.AdminTagRow::getCode, Function.identity()));
        if (byCode.size() != codes.size() || !byCode.keySet().containsAll(codes)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模板标签不存在");
        }
        return byCode;
    }

    private void insertRelations(long templateId, List<Long> tagIds) {
        if (!tagIds.isEmpty() && tagMapper.insertRelations(templateId, tagIds) != tagIds.size()) {
            throw new IllegalStateException("模板标签关联写入失败");
        }
    }

    private List<String> currentTagCodes(long templateId) {
        List<TemplateTagMapper.AdminTagRow> rows = tagMapper.findByTemplateId(templateId);
        return rows == null ? List.of() : rows.stream().map(TemplateTagMapper.AdminTagRow::getCode).toList();
    }

    private Map<Long, List<String>> tagCodesByTemplate(List<DesignTemplateMapper.AdminTemplateRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = rows.stream().map(DesignTemplateMapper.AdminTemplateRow::getId).toList();
        List<TemplateTagMapper.TagRow> relations = tagMapper.findByTemplateIds(ids);
        if (relations == null || relations.isEmpty()) {
            return Map.of();
        }
        return relations.stream().collect(Collectors.groupingBy(
            TemplateTagMapper.TagRow::getTemplateId,
            LinkedHashMap::new,
            Collectors.mapping(TemplateTagMapper.TagRow::getTagCode, Collectors.toList())
        ));
    }

    private String starterSchema(int width, int height) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("schemaVersion", 1);
        ObjectNode canvas = schema.putObject("canvas");
        canvas.put("width", width);
        canvas.put("height", height);
        ArrayNode pages = schema.putArray("pages");
        ObjectNode page = pages.addObject();
        page.put("id", "page-1");
        page.putArray("elements");
        try {
            return objectMapper.writeValueAsString(schema);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("模板初始 Schema 生成失败", exception);
        }
    }

    private Map<String, Object> metadata(Object... values) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            metadata.put((String) values[index], values[index + 1]);
        }
        return metadata;
    }

    private boolean sameNullable(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
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

    private TemplateView toView(DesignTemplateMapper.AdminTemplateRow row, List<String> tagCodes) {
        return new TemplateView(
            row.getId(), row.getName(), row.getWidth(), row.getHeight(), row.getCategoryCode(),
            row.getCoverAssetId(), row.getFeaturedRank(), row.getStatus(), row.getPublishedAt(), row.getUpdatedAt(), tagCodes
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
        Instant updatedAt,
        List<String> tagCodes
    ) {
        public TemplateView {
            tagCodes = List.copyOf(tagCodes == null ? List.of() : tagCodes);
        }
    }
}
