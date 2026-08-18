package com.example.lowcode.template.api;

import com.example.lowcode.common.api.ApiResponse;
import com.example.lowcode.common.web.TraceIdFilter;
import com.example.lowcode.template.application.TemplateQueryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/template-categories")
public class TemplateCategoryController {
    private final TemplateQueryService templateQueryService;

    public TemplateCategoryController(TemplateQueryService templateQueryService) {
        this.templateQueryService = templateQueryService;
    }

    @GetMapping
    public ApiResponse<List<TemplateQueryService.TemplateCategoryView>> list(HttpServletRequest request) {
        return ApiResponse.success(
            templateQueryService.listPublishedCategories(),
            (String) request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE)
        );
    }
}
