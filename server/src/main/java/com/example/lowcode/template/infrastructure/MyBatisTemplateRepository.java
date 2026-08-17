package com.example.lowcode.template.infrastructure;

import com.example.lowcode.template.application.TemplateQueryService;
import com.example.lowcode.template.application.TemplateRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisTemplateRepository implements TemplateRepository {
    private final DesignTemplateMapper designTemplateMapper;
    private final TemplateFieldMapper templateFieldMapper;
    private final ObjectMapper objectMapper;

    public MyBatisTemplateRepository(
        DesignTemplateMapper designTemplateMapper,
        TemplateFieldMapper templateFieldMapper,
        ObjectMapper objectMapper
    ) {
        this.designTemplateMapper = designTemplateMapper;
        this.templateFieldMapper = templateFieldMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<TemplateQueryService.TemplateSummary> findPublished() {
        return designTemplateMapper.findPublished().stream()
            .map(this::toSummary)
            .toList();
    }

    @Override
    public Optional<TemplateQueryService.TemplateDetail> findPublishedById(long templateId) {
        return Optional.ofNullable(designTemplateMapper.findPublishedById(templateId))
            .map(this::toDetail);
    }

    private TemplateQueryService.TemplateSummary toSummary(DesignTemplateMapper.TemplateRow row) {
        return new TemplateQueryService.TemplateSummary(
            row.getId(),
            row.getName(),
            row.getWidth(),
            row.getHeight(),
            row.getCoverAssetId(),
            fieldsFor(row.getId())
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
