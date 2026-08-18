package com.example.lowcode.template.infrastructure;

import com.example.lowcode.template.application.TemplatePage;
import com.example.lowcode.template.application.TemplateQueryService;
import com.example.lowcode.template.application.TemplateRepository;
import com.example.lowcode.template.application.TemplateSearchCriteria;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class MyBatisTemplateRepository implements TemplateRepository {
    private final DesignTemplateMapper designTemplateMapper;
    private final TemplateFieldMapper templateFieldMapper;
    private final TemplateCategoryMapper templateCategoryMapper;
    private final TemplateTagMapper templateTagMapper;
    private final ObjectMapper objectMapper;

    public MyBatisTemplateRepository(
        DesignTemplateMapper designTemplateMapper,
        TemplateFieldMapper templateFieldMapper,
        TemplateCategoryMapper templateCategoryMapper,
        TemplateTagMapper templateTagMapper,
        ObjectMapper objectMapper
    ) {
        this.designTemplateMapper = designTemplateMapper;
        this.templateFieldMapper = templateFieldMapper;
        this.templateCategoryMapper = templateCategoryMapper;
        this.templateTagMapper = templateTagMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public TemplatePage<TemplateQueryService.TemplateSummary> searchPublished(TemplateSearchCriteria criteria) {
        long offset = (long) (criteria.page() - 1) * criteria.pageSize();
        List<DesignTemplateMapper.TemplateRow> rows = designTemplateMapper.searchPublished(
            criteria.keyword(),
            criteria.categoryCode(),
            criteria.tagCode(),
            criteria.pageSize(),
            offset
        );
        Map<Long, List<String>> tagCodes = tagsByTemplate(rows);
        List<TemplateQueryService.TemplateSummary> items = rows.stream()
            .map(row -> toSummary(row, tagCodes.getOrDefault(row.getId(), List.of())))
            .toList();
        return new TemplatePage<>(items, criteria.page(), criteria.pageSize(), designTemplateMapper.countPublished(
            criteria.keyword(), criteria.categoryCode(), criteria.tagCode()
        ));
    }

    @Override
    public List<TemplateQueryService.TemplateCategoryView> findPublishedCategories() {
        return templateCategoryMapper.findPublished().stream()
            .map(row -> new TemplateQueryService.TemplateCategoryView(row.getCode(), row.getName(), row.getParentCode()))
            .toList();
    }

    @Override
    public boolean hasPublishedCategory(String code) {
        return templateCategoryMapper.countPublishedByCode(code) > 0;
    }

    @Override
    public boolean hasPublishedTag(String code) {
        return templateTagMapper.countPublishedByCode(code) > 0;
    }

    @Override
    public Optional<TemplateQueryService.TemplateDetail> findPublishedById(long templateId) {
        return Optional.ofNullable(designTemplateMapper.findPublishedById(templateId))
            .map(this::toDetail);
    }

    private Map<Long, List<String>> tagsByTemplate(List<DesignTemplateMapper.TemplateRow> rows) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        List<Long> templateIds = rows.stream().map(DesignTemplateMapper.TemplateRow::getId).toList();
        return templateTagMapper.findPublishedByTemplateIds(templateIds).stream()
            .collect(Collectors.groupingBy(
                TemplateTagMapper.TagRow::getTemplateId,
                HashMap::new,
                Collectors.mapping(TemplateTagMapper.TagRow::getTagCode, Collectors.toList())
            ));
    }

    private TemplateQueryService.TemplateSummary toSummary(
        DesignTemplateMapper.TemplateRow row,
        List<String> tagCodes
    ) {
        return new TemplateQueryService.TemplateSummary(
            row.getId(),
            row.getName(),
            row.getWidth(),
            row.getHeight(),
            row.getCoverAssetId(),
            coverUrl(row.getCoverAssetId()),
            row.getCategoryCode(),
            tagCodes,
            row.getPublishedAt()
        );
    }

    private TemplateQueryService.TemplateDetail toDetail(DesignTemplateMapper.TemplateRow row) {
        return new TemplateQueryService.TemplateDetail(
            row.getId(),
            row.getName(),
            row.getWidth(),
            row.getHeight(),
            row.getCoverAssetId(),
            parseSchema(row.getSchemaJson()),
            fieldsFor(row.getId())
        );
    }

    private String coverUrl(Long coverAssetId) {
        return coverAssetId == null
            ? null
            : "/api/v1/template-cover-assets/" + coverAssetId + "/content";
    }

    private List<TemplateQueryService.TemplateFieldView> fieldsFor(long templateId) {
        return templateFieldMapper.findByTemplateId(templateId).stream()
            .map(field -> new TemplateQueryService.TemplateFieldView(
                field.getFieldKey(),
                field.getLabel(),
                field.getFieldType(),
                field.isRequired(),
                field.getDefaultValue()
            ))
            .toList();
    }

    private JsonNode parseSchema(String schemaJson) {
        try {
            return objectMapper.readTree(schemaJson);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Published template has invalid schema JSON", exception);
        }
    }
}
