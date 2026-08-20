package com.example.lowcode.auth.api;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AuthRefreshCookieWriter {
    public static final String REFRESH_COOKIE = "poster_refresh_token";

    private final boolean secureCookie;
    private final Duration refreshTokenTtl;

    public AuthRefreshCookieWriter(
        @Value("${app.auth.cookie-secure:false}") boolean secureCookie,
        @Value("${app.auth.refresh-token-ttl:P30D}") Duration refreshTokenTtl
    ) {
        this.secureCookie = secureCookie;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    public void write(HttpServletResponse response, String rawToken) {
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

    public void clear(HttpServletResponse response) {
        response.setHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(REFRESH_COOKIE, "")
            .httpOnly(true)
            .secure(secureCookie)
            .sameSite("Lax")
            .path("/api/v1/auth")
            .maxAge(Duration.ZERO)
            .build()
            .toString());
    }
}
