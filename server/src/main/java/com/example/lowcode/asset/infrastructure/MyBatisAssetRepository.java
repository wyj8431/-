package com.example.lowcode.asset.infrastructure;

import com.example.lowcode.asset.application.AssetRepository;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
public class MyBatisAssetRepository implements AssetRepository {
    private final AssetUploadSessionMapper assetUploadSessionMapper;
    private final AssetMapper assetMapper;

    public MyBatisAssetRepository(
        AssetUploadSessionMapper assetUploadSessionMapper,
        AssetMapper assetMapper
    ) {
        this.assetUploadSessionMapper = assetUploadSessionMapper;
        this.assetMapper = assetMapper;
    }

    @Override
    public long createUploadSession(NewUploadSession session) {
        AssetUploadSessionMapper.SessionRow row = new AssetUploadSessionMapper.SessionRow();
        row.setTenantId(session.tenantId());
        row.setUserId(session.userId());
        row.setFileName(session.fileName());
        row.setMimeType(session.mimeType());
        row.setExpectedFileSize(session.expectedFileSize());
        row.setSha256(session.sha256());
        row.setObjectKey(session.objectKey());
        row.setExpiresAt(Timestamp.from(session.expiresAt()));
        if (assetUploadSessionMapper.insert(row) != 1 || row.getId() == null) {
            throw new IllegalStateException("Could not create asset upload session");
        }
        return row.getId();
    }

    @Override
    public Optional<UploadSession> findPendingSession(long tenantId, long userId, long sessionId) {
        return Optional.ofNullable(assetUploadSessionMapper.findPending(tenantId, userId, sessionId))
            .map(this::toUploadSession);
    }

    @Override
    public boolean markCompleted(long tenantId, long sessionId, Instant completedAt) {
        return assetUploadSessionMapper.markCompleted(tenantId, sessionId, Timestamp.from(completedAt)) == 1;
    }

    @Override
    public boolean markRejected(long tenantId, long sessionId, String reason) {
        return assetUploadSessionMapper.markRejected(tenantId, sessionId, reason) == 1;
    }

    @Override
    public boolean markExpired(long tenantId, long sessionId, Instant now) {
        return assetUploadSessionMapper.markExpired(tenantId, sessionId, Timestamp.from(now)) == 1;
    }

    @Override
    public long createAsset(NewAsset asset) {
        AssetMapper.AssetRow row = new AssetMapper.AssetRow();
        row.setTenantId(asset.tenantId());
        row.setOwnerId(asset.ownerId());
        row.setUploadSessionId(asset.uploadSessionId());
        row.setObjectKey(asset.objectKey());
        row.setFileName(asset.fileName());
        row.setMimeType(asset.mimeType());
        row.setFileSize(asset.fileSize());
        row.setSha256(asset.sha256());
        row.setWidth(asset.width());
        row.setHeight(asset.height());
        if (assetMapper.insert(row) != 1 || row.getId() == null) {
            throw new IllegalStateException("Could not create asset");
        }
        return row.getId();
    }

    private UploadSession toUploadSession(AssetUploadSessionMapper.SessionRow row) {
        return new UploadSession(
            row.getId(),
            row.getTenantId(),
            row.getUserId(),
            row.getFileName(),
            row.getMimeType(),
            row.getExpectedFileSize(),
            row.getSha256(),
            row.getObjectKey(),
            row.getExpiresAt().toInstant()
        );
    }
}
