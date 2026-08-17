package com.example.lowcode.asset.application;

import java.time.Instant;
import java.util.Optional;

public interface AssetRepository {
    long createUploadSession(NewUploadSession session);

    Optional<UploadSession> findPendingSession(long tenantId, long userId, long sessionId);

    boolean markCompleted(long tenantId, long sessionId, Instant completedAt);

    boolean markRejected(long tenantId, long sessionId, String reason);

    boolean markExpired(long tenantId, long sessionId, Instant now);

    long createAsset(NewAsset asset);

    record NewUploadSession(
        long tenantId,
        long userId,
        String fileName,
        String mimeType,
        long expectedFileSize,
        String sha256,
        String objectKey,
        Instant expiresAt
    ) {
    }

    record UploadSession(
        long id,
        long tenantId,
        long userId,
        String fileName,
        String mimeType,
        long expectedFileSize,
        String sha256,
        String objectKey,
        Instant expiresAt
    ) {
    }

    record NewAsset(
        long tenantId,
        long ownerId,
        long uploadSessionId,
        String objectKey,
        String fileName,
        String mimeType,
        long fileSize,
        String sha256,
        int width,
        int height
    ) {
    }

    record AssetRecord(
        long id,
        long tenantId,
        long ownerId,
        long uploadSessionId,
        String objectKey,
        String fileName,
        String mimeType,
        long fileSize,
        String sha256,
        int width,
        int height,
        Instant createdAt
    ) {
    }
}
