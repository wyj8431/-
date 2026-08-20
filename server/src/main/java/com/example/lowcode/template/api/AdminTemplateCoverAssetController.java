package com.example.lowcode.template.api;

import com.example.lowcode.auth.security.CurrentUser;
import com.example.lowcode.common.api.ApiResponse;
import com.example.lowcode.common.web.TraceIdFilter;
import com.example.lowcode.template.application.TemplateCoverAdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/template-cover-assets")
public class AdminTemplateCoverAssetController {
    private final TemplateCoverAdminService service;

    public AdminTemplateCoverAssetController(TemplateCoverAdminService service) {
        this.service = service;
    }

    @PostMapping("/presign")
    public ApiResponse<TemplateCoverAdminService.PresignResult> presign(
        @Valid @RequestBody PresignRequest body,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest request
    ) {
        return success(service.presign(CurrentUser.fromJwt(jwt), new TemplateCoverAdminService.PresignCommand(
            body.fileName(), body.mimeType(), body.fileSize(), body.sha256()
        )), request);
    }

    @PostMapping("/complete")
    public ApiResponse<TemplateCoverAdminService.CoverView> complete(
        @Valid @RequestBody CompleteRequest body,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest request
    ) {
        return success(service.complete(CurrentUser.fromJwt(jwt), new TemplateCoverAdminService.CompleteCommand(body.sessionId())), request);
    }

    @GetMapping
    public ApiResponse<List<TemplateCoverAdminService.CoverView>> list(
        @RequestParam(required = false) String status,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest request
    ) {
        return success(service.list(CurrentUser.fromJwt(jwt), status), request);
    }

    @PatchMapping("/{coverAssetId}/status")
    public ApiResponse<TemplateCoverAdminService.CoverView> changeStatus(
        @PathVariable long coverAssetId,
        @RequestBody ChangeStatusRequest body,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest request
    ) {
        return success(service.changeStatus(CurrentUser.fromJwt(jwt), coverAssetId, body.status()), request);
    }

    @DeleteMapping("/{coverAssetId}")
    public ApiResponse<Void> delete(
        @PathVariable long coverAssetId,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest request
    ) {
        service.delete(CurrentUser.fromJwt(jwt), coverAssetId);
        return success(null, request);
    }

    private <T> ApiResponse<T> success(T value, HttpServletRequest request) {
        return ApiResponse.success(value, (String) request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE));
    }

    public record PresignRequest(
        @NotBlank @Size(max = 255) String fileName,
        @NotBlank @Pattern(regexp = "image/(jpeg|png|webp)") String mimeType,
        @Min(1) @Max(10 * 1024 * 1024) long fileSize,
        @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String sha256
    ) {}

    public record CompleteRequest(@Min(1) long sessionId) {}
    public record ChangeStatusRequest(String status) {}
}
