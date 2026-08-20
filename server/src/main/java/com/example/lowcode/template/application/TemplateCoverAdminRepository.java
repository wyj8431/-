package com.example.lowcode.template.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TemplateCoverAdminRepository {
    long createUploadSession(NewUploadSession session);

    Optional<UploadSession> findPendingSession(long tenantId, long userId, long sessionId);

    boolean markCompleted(long sessionId, Instant completedAt);

    boolean markRejected(long sessionId, String reason);

    long createAsset(NewAsset asset);

    List<CoverAssetRecord> findAll(String status);

    Optional<CoverAssetRecord> findById(long coverAssetId);

    boolean updateStatus(long coverAssetId, String status);

    int countReferences(long coverAssetId);

    boolean deleteById(long coverAssetId);

    Optional<Long> findTemplateCoverId(long templateId);

    boolean updateTemplateCover(long templateId, Long coverAssetId);

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
        long uploadSessionId,
        String objectKey,
        String mimeType,
        long fileSize,
        String sha256,
        int width,
        int height
    ) {
    }

    record CoverAssetRecord(
        long id,
        String objectKey,
        String mimeType,
        long fileSize,
        String sha256,
        int width,
        int height,
        String status,
        Instant createdAt,
        Instant updatedAt
    ) {
    }
}
