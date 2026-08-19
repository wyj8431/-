package com.example.lowcode.auth.application;

public record SessionIdentity(
    long userId,
    long tenantId,
    String userStatus,
    String tenantStatus,
    String tenantRole,
    int securityVersion
) {
}
