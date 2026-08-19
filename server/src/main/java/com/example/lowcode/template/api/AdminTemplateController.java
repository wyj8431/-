package com.example.lowcode.template.api;

import com.example.lowcode.auth.security.CurrentUser;
import com.example.lowcode.common.api.ApiResponse;
import com.example.lowcode.common.web.TraceIdFilter;
import com.example.lowcode.template.application.TemplateAdminService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/templates")
public class AdminTemplateController {
    private final TemplateAdminService templateService;

    public AdminTemplateController(TemplateAdminService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    public ApiResponse<List<TemplateAdminService.TemplateView>> list(
        @RequestParam(required = false) String status,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest request
    ) {
        return ApiResponse.success(
            templateService.list(CurrentUser.fromJwt(jwt), status),
            (String) request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE)
        );
    }

    @PatchMapping("/{templateId}/status")
    public ApiResponse<TemplateAdminService.TemplateView> changeStatus(
        @PathVariable long templateId,
        @RequestBody ChangeStatusRequest body,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest request
    ) {
        return ApiResponse.success(
            templateService.changeStatus(CurrentUser.fromJwt(jwt), templateId, body.status()),
            (String) request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE)
        );
    }

    public record ChangeStatusRequest(String status) {
    }
}
