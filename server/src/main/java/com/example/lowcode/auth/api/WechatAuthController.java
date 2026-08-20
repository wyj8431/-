package com.example.lowcode.auth.api;

import com.example.lowcode.auth.application.WechatAuthService;
import com.example.lowcode.auth.infrastructure.WechatOAuthProperties;
import com.example.lowcode.auth.security.CurrentUser;
import com.example.lowcode.common.api.ApiResponse;
import com.example.lowcode.common.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;

@RestController
@RequestMapping("/api/v1/auth/wechat")
public class WechatAuthController {
    private final WechatAuthService wechatAuthService;
    private final AuthRefreshCookieWriter refreshCookieWriter;
    private final URI publicBaseUri;

    public WechatAuthController(
        WechatAuthService wechatAuthService,
        AuthRefreshCookieWriter refreshCookieWriter,
        WechatOAuthProperties properties
    ) {
        this.wechatAuthService = wechatAuthService;
        this.refreshCookieWriter = refreshCookieWriter;
        this.publicBaseUri = parsePublicBaseUri(properties.getWebPublicBaseUrl());
    }

    @PostMapping("/login/authorize")
    public ApiResponse<AuthorizeResult> beginLogin(
        @RequestBody(required = false) AuthorizeRequest request,
        HttpServletRequest servletRequest
    ) {
        WechatAuthService.PreparedAuthorization authorization = wechatAuthService.beginLogin(
            request == null ? null : request.returnTo()
        );
        return ApiResponse.success(new AuthorizeResult(authorization.authorizeUrl()), traceId(servletRequest));
    }

    @PostMapping("/bind/authorize")
    public ApiResponse<AuthorizeResult> beginBinding(
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest servletRequest
    ) {
        WechatAuthService.PreparedAuthorization authorization = wechatAuthService.beginBind(CurrentUser.fromJwt(jwt));
        return ApiResponse.success(new AuthorizeResult(authorization.authorizeUrl()), traceId(servletRequest));
    }

    @GetMapping("/callback")
    public void callback(
        @RequestParam(required = false) String code,
        @RequestParam(required = false) String state,
        HttpServletRequest servletRequest,
        HttpServletResponse servletResponse
    ) throws IOException {
        WechatAuthService.CallbackResult result = wechatAuthService.complete(
            code,
            state,
            servletRequest.getHeader(HttpHeaders.USER_AGENT),
            servletRequest.getRemoteAddr()
        );
        refreshCookieWriter.write(servletResponse, result.refreshToken());
        servletResponse.sendRedirect(resultUrl(result));
    }

    private String resultUrl(WechatAuthService.CallbackResult result) {
        return UriComponentsBuilder.fromUri(publicBaseUri)
            .path("/auth/wechat/result")
            .queryParam("result", result.kind().name().toLowerCase())
            .queryParam("returnTo", result.returnPath())
            .build()
            .encode()
            .toUriString();
    }

    private URI parsePublicBaseUri(String configuredValue) {
        try {
            URI uri = URI.create(configuredValue == null ? "" : configuredValue.trim());
            if (("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                && uri.getHost() != null
                && uri.getUserInfo() == null
                && uri.getQuery() == null
                && uri.getFragment() == null) {
                return uri;
            }
        } catch (IllegalArgumentException ignored) {
            // The application cannot safely construct a callback redirect from this value.
        }
        throw new IllegalArgumentException("app.wechat.web-public-base-url must be an HTTP(S) site URL");
    }

    private String traceId(HttpServletRequest request) {
        return (String) request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
    }

    public record AuthorizeRequest(String returnTo) {
    }

    public record AuthorizeResult(String authorizeUrl) {
    }
}
