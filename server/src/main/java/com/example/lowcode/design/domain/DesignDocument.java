package com.example.lowcode.design.domain;

public record DesignDocument(
    long id,
    long tenantId,
    long ownerId,
    long templateId,
    String name,
    int width,
    int height,
    int currentVersion
) {
}
