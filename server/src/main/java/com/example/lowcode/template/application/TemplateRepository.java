package com.example.lowcode.template.application;

import java.util.List;
import java.util.Optional;

public interface TemplateRepository {
    List<TemplateQueryService.TemplateSummary> findPublished();

    Optional<TemplateQueryService.TemplateDetail> findPublishedById(long templateId);
}
