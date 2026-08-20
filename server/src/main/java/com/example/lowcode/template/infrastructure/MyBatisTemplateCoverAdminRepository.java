package com.example.lowcode.template.infrastructure;

import com.example.lowcode.template.application.TemplateCoverAdminRepository;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisTemplateCoverAdminRepository implements TemplateCoverAdminRepository {
    private final TemplateCoverAdminMapper mapper;

    public MyBatisTemplateCoverAdminRepository(TemplateCoverAdminMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public long createUploadSession(NewUploadSession session) {
        TemplateCoverAdminMapper.SessionRow row = new TemplateCoverAdminMapper.SessionRow();
        row.setTenantId(session.tenantId());
        row.setUserId(session.userId());
        row.setFileName(session.fileName());
        row.setMimeType(session.mimeType());
        row.setExpectedFileSize(session.expectedFileSize());
        row.setSha256(session.sha256());
        row.setObjectKey(session.objectKey());
        row.setExpiresAt(Timestamp.from(session.expiresAt()));
        if (mapper.insertUploadSession(row) != 1 || row.getId() == null) throw new IllegalStateException("封面上传会话创建失败");
        return row.getId();
    }

    @Override
    public Optional<UploadSession> findPendingSession(long tenantId, long userId, long sessionId) {
        return Optional.ofNullable(mapper.findPendingSession(tenantId, userId, sessionId)).map(row -> new UploadSession(
            row.getId(), row.getTenantId(), row.getUserId(), row.getFileName(), row.getMimeType(),
            row.getExpectedFileSize(), row.getSha256(), row.getObjectKey(), row.getExpiresAt().toInstant()
        ));
    }

    @Override
    public boolean markCompleted(long sessionId, Instant completedAt) {
        return mapper.markCompleted(sessionId, Timestamp.from(completedAt)) == 1;
    }

    @Override
    public boolean markRejected(long sessionId, String reason) {
        return mapper.markRejected(sessionId, reason) == 1;
    }

    @Override
    public long createAsset(NewAsset asset) {
        TemplateCoverAdminMapper.AssetRow row = new TemplateCoverAdminMapper.AssetRow();
        row.setUploadSessionId(asset.uploadSessionId());
        row.setObjectKey(asset.objectKey());
        row.setMimeType(asset.mimeType());
        row.setFileSize(asset.fileSize());
        row.setSha256(asset.sha256());
        row.setWidth(asset.width());
        row.setHeight(asset.height());
        if (mapper.insertAsset(row) != 1 || row.getId() == null) throw new IllegalStateException("封面素材创建失败");
        return row.getId();
    }

    @Override
    public List<CoverAssetRecord> findAll(String status) {
        return mapper.findAll(status).stream().map(this::toRecord).toList();
    }

    @Override
    public Optional<CoverAssetRecord> findById(long coverAssetId) {
        return Optional.ofNullable(mapper.findById(coverAssetId)).map(this::toRecord);
    }

    @Override
    public boolean updateStatus(long coverAssetId, String status) { return mapper.updateStatus(coverAssetId, status) == 1; }

    @Override
    public int countReferences(long coverAssetId) { return mapper.countReferences(coverAssetId); }

    @Override
    public boolean deleteById(long coverAssetId) { return mapper.deleteById(coverAssetId) == 1; }

    @Override
    public Optional<Long> findTemplateCoverId(long templateId) { return Optional.ofNullable(mapper.findTemplateCoverId(templateId)); }

    @Override
    public boolean updateTemplateCover(long templateId, Long coverAssetId) { return mapper.updateTemplateCover(templateId, coverAssetId) == 1; }

    private CoverAssetRecord toRecord(TemplateCoverAdminMapper.CoverRow row) {
        return new CoverAssetRecord(row.getId(), row.getObjectKey(), row.getMimeType(), row.getFileSize(), row.getSha256(),
            row.getWidth(), row.getHeight(), row.getStatus(), row.getCreatedAt().toInstant(), row.getUpdatedAt().toInstant());
    }
}
