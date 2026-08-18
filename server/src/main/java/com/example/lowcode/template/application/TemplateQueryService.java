package com.example.lowcode.template.application;

import com.example.lowcode.common.exception.BusinessException;
import com.example.lowcode.common.exception.ErrorCode;
import com.example.lowcode.design.domain.DesignSchemaValidator;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class TemplateQueryService {
    private static final Pattern PUBLIC_CODE = Pattern.compile("^[a-z0-9-]{1,64}$");
    private static final int MAX_KEYWORD_LENGTH = 64;

    private final TemplateRepository templateRepository;
    private final DesignSchemaValidator designSchemaValidator;

    @Autowired
    public TemplateQueryService(TemplateRepository templateRepository, DesignSchemaValidator designSchemaValidator) {
        this.templateRepository = templateRepository;
        this.designSchemaValidator = designSchemaValidator;
    }

    public TemplateQueryService(TemplateRepository templateRepository) {
        this(templateRepository, new DesignSchemaValidator());
    }

    public TemplatePage<TemplateSummary> searchPublished(TemplateSearchCriteria criteria) {
        TemplateSearchCriteria normalized = normalize(criteria);
        if (normalized.categoryCode() != null && !templateRepository.hasPublishedCategory(normalized.categoryCode())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "分类不存在或未发布");
        }
        if (normalized.tagCode() != null && !templateRepository.hasPublishedTag(normalized.tagCode())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "标签不存在或未发布");
        }
        return templateRepository.searchPublished(normalized);
    }

    public List<TemplateCategoryView> listPublishedCategories() {
        return templateRepository.findPublishedCategories();
    }

    public List<TemplateTagView> listPublishedTags() {
        return templateRepository.findPublishedTags();
    }

    public List<TemplateSummary> findPublishedByIds(List<Long> templateIds) {
        if (templateIds == null || templateIds.isEmpty()) {
            return List.of();
        }
        List<Long> normalizedIds = templateIds.stream()
            .filter(id -> id != null && id > 0)
            .distinct()
            .toList();
        return normalizedIds.isEmpty() ? List.of() : templateRepository.findPublishedByIds(normalizedIds);
    }

    public TemplateDetail findPublishedById(long templateId) {
        TemplateDetail template = templateRepository.findPublishedById(templateId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "模板不存在或未发布"));
        designSchemaValidator.validate(template.schema());
        return template;
    }

    private TemplateSearchCriteria normalize(TemplateSearchCriteria criteria) {
        if (criteria == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "查询条件无效");
        }
        if (criteria.page() < TemplateSearchCriteria.DEFAULT_PAGE) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "页码无效");
        }
        if (criteria.pageSize() < 1 || criteria.pageSize() > TemplateSearchCriteria.MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "每页数量无效");
        }
        String keyword = trimToNull(criteria.keyword());
        if (keyword != null && keyword.length() > MAX_KEYWORD_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "关键词过长");
        }
        String categoryCode = normalizeCode(criteria.categoryCode(), "分类");
        String tagCode = normalizeCode(criteria.tagCode(), "标签");
        return new TemplateSearchCriteria(keyword, categoryCode, tagCode, criteria.page(), criteria.pageSize());
    }

    private String normalizeCode(String value, String label) {
        String normalized = trimToNull(value);
        if (normalized != null && !PUBLIC_CODE.matcher(normalized).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, label + "编码无效");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public record TemplateCategoryView(String code, String name, String parentCode) {
    }

    public record TemplateTagView(String code, String name) {
    }

    public record TemplateFieldView(
        String fieldKey,
        String label,
        String fieldType,
        boolean required,
        String defaultValue
    ) {
    }

    public record TemplateSummary(
        long id,
        String name,
        int width,
        int height,
        Long coverAssetId,
        String coverUrl,
        String categoryCode,
        List<String> tagCodes,
        Instant publishedAt
    ) {
    }

    public record TemplateDetail(
        long id,
        String name,
        int width,
        int height,
        Long coverAssetId,
        JsonNode schema,
        List<TemplateFieldView> fields
    ) {
    }
}
