package com.example.lowcode.asset.api;

import com.example.lowcode.asset.application.AssetService;
import com.example.lowcode.auth.security.CurrentUser;
import com.example.lowcode.common.api.ApiResponse;
import com.example.lowcode.common.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assets")
public class AssetController {
    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @PostMapping("/presign")
    public ApiResponse<AssetService.PresignResult> presign(
        @Valid @RequestBody PresignRequest request,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest servletRequest
    ) {
        return success(
            assetService.presign(
                CurrentUser.fromJwt(jwt),
                new AssetService.PresignCommand(request.fileName(), request.mimeType(), request.fileSize(), request.sha256())
            ),
            servletRequest
        );
    }

    @PostMapping("/complete")
    public ApiResponse<AssetService.AssetView> complete(
        @Valid @RequestBody CompleteRequest request,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest servletRequest
    ) {
        return success(
            assetService.complete(CurrentUser.fromJwt(jwt), new AssetService.CompleteCommand(request.sessionId())),
            servletRequest
        );
    }

    private <T> ApiResponse<T> success(T data, HttpServletRequest servletRequest) {
        return ApiResponse.success(data, (String) servletRequest.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE));
    }

    public record PresignRequest(
        @NotBlank @Size(max = 255) String fileName,
        @NotBlank @Pattern(regexp = "image/(jpeg|png|webp)") String mimeType,
        @Min(1) @Max(10 * 1024 * 1024) long fileSize,
        @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String sha256
    ) {
    }

    public record CompleteRequest(@Min(1) long sessionId) {
    }
}
