package com.example.lowcode.template.application;

public record TemplateSearchCriteria(
    String keyword,
    String categoryCode,
    String tagCode,
    int page,
    int pageSize
) {
    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_PAGE_SIZE = 24;
    public static final int MAX_PAGE_SIZE = 48;
}
