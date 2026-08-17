package com.example.lowcode.design.api;

import com.example.lowcode.auth.security.CurrentUser;
import com.example.lowcode.common.api.ApiResponse;
import com.example.lowcode.common.web.TraceIdFilter;
import com.example.lowcode.design.application.DesignService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/designs")
public class DesignController {
    private final DesignService designService;

    public DesignController(DesignService designService) {
        this.designService = designService;
    }

    @PostMapping
    public ApiResponse<DesignService.DesignView> create(
        @Valid @RequestBody CreateDesignRequest request,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest servletRequest
    ) {
        return success(
            designService.create(currentUser(jwt), new DesignService.CreateDesignCommand(request.templateId(), request.name())),
            servletRequest
        );
    }

    @GetMapping("/{documentId}")
    public ApiResponse<DesignService.DesignView> findById(
        @PathVariable long documentId,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest servletRequest
    ) {
        return success(designService.findById(currentUser(jwt), documentId), servletRequest);
    }

    @PatchMapping("/{documentId}")
    public ApiResponse<DesignService.DesignView> save(
        @PathVariable long documentId,
        @Valid @RequestBody SaveDesignRequest request,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest servletRequest
    ) {
        return success(
            designService.save(
                currentUser(jwt),
                documentId,
                new DesignService.SaveDesignCommand(request.baseVersion(), request.schema())
            ),
            servletRequest
        );
    }

    @GetMapping("/{documentId}/versions")
    public ApiResponse<List<DesignService.VersionView>> versions(
        @PathVariable long documentId,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest servletRequest
    ) {
        return success(designService.findVersions(currentUser(jwt), documentId), servletRequest);
    }

    private CurrentUser currentUser(Jwt jwt) {
        return CurrentUser.fromJwt(jwt);
    }

    private <T> ApiResponse<T> success(T data, HttpServletRequest request) {
        return ApiResponse.success(data, (String) request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE));
    }

    public record CreateDesignRequest(@Min(1) long templateId, @NotBlank @Size(max = 128) String name) {
    }

    public record SaveDesignRequest(@Min(1) int baseVersion, @NotNull JsonNode schema) {
    }
}
