package com.example.lowcode.template.api;

import com.example.lowcode.common.api.ApiResponse;
import com.example.lowcode.common.web.TraceIdFilter;
import com.example.lowcode.template.application.TemplatePage;
import com.example.lowcode.template.application.TemplateQueryService;
import com.example.lowcode.template.application.TemplateSearchCriteria;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/templates")
public class TemplateController {
    private final TemplateQueryService templateQueryService;

    public TemplateController(TemplateQueryService templateQueryService) {
        this.templateQueryService = templateQueryService;
    }

    @GetMapping
    public ApiResponse<TemplatePage<TemplateQueryService.TemplateSummary>> list(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String categoryCode,
        @RequestParam(required = false) String tagCode,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "24") int pageSize,
        HttpServletRequest request
    ) {
        return ApiResponse.success(
            templateQueryService.searchPublished(
                new TemplateSearchCriteria(keyword, categoryCode, tagCode, page, pageSize)
            ),
            (String) request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE)
        );
    }

    @GetMapping("/{templateId}")
    public ApiResponse<TemplateQueryService.TemplateDetail> detail(
        @PathVariable long templateId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(
            templateQueryService.findPublishedById(templateId),
            (String) request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE)
        );
    }
}
