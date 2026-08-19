package com.example.lowcode.auth.api;

import com.example.lowcode.auth.application.AuthService;
import com.example.lowcode.common.api.ApiResponse;
import com.example.lowcode.common.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    static final String REFRESH_COOKIE = "poster_refresh_token";

    private final AuthService authService;
    private final boolean secureCookie;
    private final Duration refreshTokenTtl;

    public AuthController(
        AuthService authService,
        @Value("${app.auth.cookie-secure:false}") boolean secureCookie,
        @Value("${app.auth.refresh-token-ttl:P30D}") Duration refreshTokenTtl
    ) {
        this.authService = authService;
        this.secureCookie = secureCookie;
        this.refreshTokenTtl = refreshTokenTtl;
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
        writeRefreshCookie(servletResponse, result.refreshToken());
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
            writeRefreshCookie(servletResponse, result.refreshToken());
            return ApiResponse.success(result, traceId(servletRequest));
        } catch (RuntimeException exception) {
            clearRefreshCookie(servletResponse);
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
            clearRefreshCookie(servletResponse);
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

    private void writeRefreshCookie(HttpServletResponse response, String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        response.setHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(REFRESH_COOKIE, rawToken)
            .httpOnly(true)
            .secure(secureCookie)
            .sameSite("Lax")
            .path("/api/v1/auth")
            .maxAge(refreshTokenTtl)
            .build()
            .toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        response.setHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(REFRESH_COOKIE, "")
            .httpOnly(true)
            .secure(secureCookie)
            .sameSite("Lax")
            .path("/api/v1/auth")
            .maxAge(Duration.ZERO)
            .build()
            .toString());
    }

    private String traceId(HttpServletRequest request) {
        return (String) request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
    }

    public record LoginRequest(@NotBlank String phone, @NotBlank String verificationCode) {
    }
}
