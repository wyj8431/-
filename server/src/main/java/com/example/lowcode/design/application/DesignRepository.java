package com.example.lowcode.design.application;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DesignRepository {
    long insertDocument(
        long tenantId,
        long ownerId,
        long templateId,
        String name,
        int width,
        int height,
        JsonNode templateFieldSnapshot
    );

    void insertVersion(long tenantId, long documentId, int versionNo, JsonNode schema, long createdBy);

    void insertPermission(long tenantId, long documentId, long userId, String role);

    Optional<DesignSnapshot> findSnapshot(long tenantId, long documentId);

    boolean canView(long tenantId, long documentId, long userId);

    boolean canEdit(long tenantId, long documentId, long userId);

    boolean advanceVersion(long tenantId, long documentId, int baseVersion);

    List<DesignService.VersionView> findVersions(long tenantId, long documentId);

    record DesignSnapshot(
        long id,
        long templateId,
        String name,
        int width,
        int height,
        int currentVersion,
        JsonNode schema,
        Instant updatedAt
    ) {
    }
}
