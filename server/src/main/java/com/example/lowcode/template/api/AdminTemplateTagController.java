package com.example.lowcode.template.api;

import com.example.lowcode.auth.security.CurrentUser;
import com.example.lowcode.common.api.ApiResponse;
import com.example.lowcode.common.web.TraceIdFilter;
import com.example.lowcode.template.application.TemplateTagAdminService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/template-tags")
public class AdminTemplateTagController {
    private final TemplateTagAdminService tagService;

    public AdminTemplateTagController(TemplateTagAdminService tagService) {
        this.tagService = tagService;
    }

    @GetMapping
    public ApiResponse<List<TemplateTagAdminService.TagView>> list(
        @RequestParam(required = false) String status,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest request
    ) {
        return ApiResponse.success(
            tagService.list(CurrentUser.fromJwt(jwt), status),
            (String) request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE)
        );
    }

    @PostMapping
    public ApiResponse<TemplateTagAdminService.TagView> create(
        @RequestBody CreateTagRequest body,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest request
    ) {
        return ApiResponse.success(
            tagService.create(CurrentUser.fromJwt(jwt), body.code(), body.name(), body.sortOrder()),
            (String) request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE)
        );
    }

    @PatchMapping("/{code}")
    public ApiResponse<TemplateTagAdminService.TagView> update(
        @PathVariable String code,
        @RequestBody UpdateTagRequest body,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest request
    ) {
        return ApiResponse.success(
            tagService.update(CurrentUser.fromJwt(jwt), code, body.name(), body.sortOrder()),
            (String) request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE)
        );
    }

    @DeleteMapping("/{code}")
    public ApiResponse<Void> delete(
        @PathVariable String code,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest request
    ) {
        tagService.delete(CurrentUser.fromJwt(jwt), code);
        return ApiResponse.success(null, (String) request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE));
    }

    @PatchMapping("/{code}/status")
    public ApiResponse<TemplateTagAdminService.TagView> changeStatus(
        @PathVariable String code,
        @RequestBody ChangeStatusRequest body,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest request
    ) {
        return ApiResponse.success(
            tagService.changeStatus(CurrentUser.fromJwt(jwt), code, body.status()),
            (String) request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE)
        );
    }

    public record ChangeStatusRequest(String status) {
    }

    public record CreateTagRequest(String code, String name, Integer sortOrder) {
    }

    public record UpdateTagRequest(String name, Integer sortOrder) {
    }
}
