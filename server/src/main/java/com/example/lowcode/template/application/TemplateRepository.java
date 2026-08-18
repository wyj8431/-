package com.example.lowcode.template.application;

import java.util.List;
import java.util.Optional;

public interface TemplateRepository {
    TemplatePage<TemplateQueryService.TemplateSummary> searchPublished(TemplateSearchCriteria criteria);

    List<TemplateQueryService.TemplateSummary> findPublishedByIds(List<Long> templateIds);

    List<TemplateQueryService.TemplateCategoryView> findPublishedCategories();

    List<TemplateQueryService.TemplateTagView> findPublishedTags();

    boolean hasPublishedCategory(String code);

    boolean hasPublishedTag(String code);

    Optional<TemplateQueryService.TemplateDetail> findPublishedById(long templateId);
}
