package com.example.lowcode.auth.api;

import com.example.lowcode.auth.application.AuthService;
import com.example.lowcode.common.api.ApiResponse;
import com.example.lowcode.common.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    static final String REFRESH_COOKIE = AuthRefreshCookieWriter.REFRESH_COOKIE;

    private final AuthService authService;
    private final AuthRefreshCookieWriter refreshCookieWriter;

    public AuthController(
        AuthService authService,
        AuthRefreshCookieWriter refreshCookieWriter
    ) {
        this.authService = authService;
        this.refreshCookieWriter = refreshCookieWriter;
    }

    @PostMapping("/login")
    public ApiResponse<AuthService.LoginResult> login(
        @Valid @RequestBody LoginRequest request,
        HttpServletRequest servletRequest,
        HttpServletResponse servletResponse
    ) {
        AuthService.LoginResult result = authService.login(
            new AuthService.LoginCommand(request.phone(), request.verificationCode()),
            servletRequest.getHeader(HttpHeaders.USER_AGENT),
            servletRequest.getRemoteAddr()
        );
        refreshCookieWriter.write(servletResponse, result.refreshToken());
        return ApiResponse.success(result, (String) servletRequest.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthService.RefreshResult> refresh(
        @CookieValue(value = REFRESH_COOKIE, required = false) String refreshToken,
        HttpServletRequest servletRequest,
        HttpServletResponse servletResponse
    ) {
        try {
            AuthService.RefreshResult result = authService.refresh(
                refreshToken,
                servletRequest.getHeader(HttpHeaders.USER_AGENT),
                servletRequest.getRemoteAddr()
            );
            refreshCookieWriter.write(servletResponse, result.refreshToken());
            return ApiResponse.success(result, traceId(servletRequest));
        } catch (RuntimeException exception) {
            refreshCookieWriter.clear(servletResponse);
            throw exception;
        }
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
        @CookieValue(value = REFRESH_COOKIE, required = false) String refreshToken,
        HttpServletRequest servletRequest,
        HttpServletResponse servletResponse
    ) {
        try {
            authService.logout(refreshToken);
            return ApiResponse.success(null, traceId(servletRequest));
        } finally {
            refreshCookieWriter.clear(servletResponse);
        }
    }

    @GetMapping("/me")
    public ApiResponse<AuthService.MeResult> me(
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
            authService.me(com.example.lowcode.auth.security.CurrentUser.fromJwt(jwt)),
            traceId(servletRequest)
        );
    }

    private String traceId(HttpServletRequest request) {
        return (String) request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
    }

    public record LoginRequest(@NotBlank String phone, @NotBlank String verificationCode) {
    }
}
