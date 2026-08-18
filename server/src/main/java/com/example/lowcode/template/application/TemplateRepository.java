package com.example.lowcode.template.application;

import java.util.List;
import java.util.Optional;

public interface TemplateRepository {
    TemplatePage<TemplateQueryService.TemplateSummary> searchPublished(TemplateSearchCriteria criteria);

    List<TemplateQueryService.TemplateCategoryView> findPublishedCategories();

    boolean hasPublishedCategory(String code);

    boolean hasPublishedTag(String code);

    Optional<TemplateQueryService.TemplateDetail> findPublishedById(long templateId);
}
