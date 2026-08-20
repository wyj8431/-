package com.example.lowcode.template.api;

import com.example.lowcode.auth.security.CurrentUser;
import com.example.lowcode.common.api.ApiResponse;
import com.example.lowcode.common.web.TraceIdFilter;
import com.example.lowcode.template.application.TemplateAdminService;
import com.example.lowcode.template.application.TemplateCoverAdminService;
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
@RequestMapping("/api/v1/admin/templates")
public class AdminTemplateController {
    private final TemplateAdminService templateService;
    private final TemplateCoverAdminService coverService;

    public AdminTemplateController(TemplateAdminService templateService, TemplateCoverAdminService coverService) {
        this.templateService = templateService;
        this.coverService = coverService;
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

    @PostMapping
    public ApiResponse<TemplateAdminService.TemplateView> create(
        @RequestBody CreateTemplateRequest body,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest request
    ) {
        return ApiResponse.success(
            templateService.create(
                CurrentUser.fromJwt(jwt), body.name(), body.width(), body.height(),
                body.categoryCode(), body.tagCodes(), body.featuredRank()
            ),
            (String) request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE)
        );
    }

    @PatchMapping("/{templateId}")
    public ApiResponse<TemplateAdminService.TemplateView> update(
        @PathVariable long templateId,
        @RequestBody UpdateTemplateRequest body,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest request
    ) {
        return ApiResponse.success(
            templateService.update(
                CurrentUser.fromJwt(jwt), templateId, body.name(), body.width(), body.height(),
                body.categoryCode(), body.tagCodes(), body.featuredRank()
            ),
            (String) request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE)
        );
    }

    @DeleteMapping("/{templateId}")
    public ApiResponse<Void> delete(
        @PathVariable long templateId,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest request
    ) {
        templateService.delete(CurrentUser.fromJwt(jwt), templateId);
        return ApiResponse.success(null, (String) request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE));
    }

    @PatchMapping("/{templateId}/cover")
    public ApiResponse<TemplateCoverAdminService.BindingResult> bindCover(
        @PathVariable long templateId,
        @RequestBody BindCoverRequest body,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest request
    ) {
        return ApiResponse.success(
            coverService.bindTemplateCover(CurrentUser.fromJwt(jwt), templateId, body.coverAssetId()),
            (String) request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE)
        );
    }

    public record ChangeStatusRequest(String status) {
    }

    public record CreateTemplateRequest(
        String name,
        int width,
        int height,
        String categoryCode,
        List<String> tagCodes,
        Integer featuredRank
    ) {
    }

    public record UpdateTemplateRequest(
        String name,
        int width,
        int height,
        String categoryCode,
        List<String> tagCodes,
        Integer featuredRank
    ) {
    }

    public record BindCoverRequest(Long coverAssetId) {
    }
}
