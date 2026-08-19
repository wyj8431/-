package com.example.lowcode.auth.application;

import java.time.Instant;

public record NewRefreshToken(
    String tokenHash,
    String familyId,
    long userId,
    long tenantId,
    int securityVersion,
    Instant expiresAt,
    String userAgent,
    String ipAddress
) {
}
