package com.example.lowcode.template.api;

import com.example.lowcode.common.api.ApiResponse;
import com.example.lowcode.common.web.TraceIdFilter;
import com.example.lowcode.template.application.TemplateQueryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/templates")
public class TemplateController {
    private final TemplateQueryService templateQueryService;

    public TemplateController(TemplateQueryService templateQueryService) {
        this.templateQueryService = templateQueryService;
    }

    @GetMapping
    public ApiResponse<List<TemplateQueryService.TemplateSummary>> list(HttpServletRequest request) {
        return ApiResponse.success(
            templateQueryService.listPublished(),
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
