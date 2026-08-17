package com.example.lowcode.template.application;

import com.example.lowcode.common.exception.BusinessException;
import com.example.lowcode.common.exception.ErrorCode;
import com.example.lowcode.design.domain.DesignSchemaValidator;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TemplateQueryService {
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

    public List<TemplateSummary> listPublished() {
        return templateRepository.findPublished();
    }

    public TemplateDetail findPublishedById(long templateId) {
        TemplateDetail template = templateRepository.findPublishedById(templateId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "模板不存在或未发布"));
        designSchemaValidator.validate(template.schema());
        return template;
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
        List<TemplateFieldView> fields
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
