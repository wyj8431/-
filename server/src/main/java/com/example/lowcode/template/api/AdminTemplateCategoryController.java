package com.example.lowcode.template.api;

import com.example.lowcode.auth.security.CurrentUser;
import com.example.lowcode.common.api.ApiResponse;
import com.example.lowcode.common.web.TraceIdFilter;
import com.example.lowcode.template.application.TemplateCategoryAdminService;
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
@RequestMapping("/api/v1/admin/template-categories")
public class AdminTemplateCategoryController {
    private final TemplateCategoryAdminService categoryService;

    public AdminTemplateCategoryController(TemplateCategoryAdminService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ApiResponse<List<TemplateCategoryAdminService.CategoryView>> list(
        @RequestParam(required = false) String status,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest request
    ) {
        return ApiResponse.success(
            categoryService.list(CurrentUser.fromJwt(jwt), status),
            (String) request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE)
        );
    }

    @PatchMapping("/{code}/status")
    public ApiResponse<TemplateCategoryAdminService.CategoryView> changeStatus(
        @PathVariable String code,
        @RequestBody ChangeStatusRequest body,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest request
    ) {
        return ApiResponse.success(
            categoryService.changeStatus(CurrentUser.fromJwt(jwt), code, body.status()),
            (String) request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE)
        );
    }

    public record ChangeStatusRequest(String status) {
    }
}
