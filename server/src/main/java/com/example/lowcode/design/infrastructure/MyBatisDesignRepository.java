package com.example.lowcode.design.infrastructure;

import com.example.lowcode.design.application.DesignRepository;
import com.example.lowcode.design.application.DesignService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisDesignRepository implements DesignRepository {
    private final DesignDocumentMapper designDocumentMapper;
    private final DesignVersionMapper designVersionMapper;
    private final DesignPermissionMapper designPermissionMapper;
    private final ObjectMapper objectMapper;

    public MyBatisDesignRepository(
        DesignDocumentMapper designDocumentMapper,
        DesignVersionMapper designVersionMapper,
        DesignPermissionMapper designPermissionMapper,
        ObjectMapper objectMapper
    ) {
        this.designDocumentMapper = designDocumentMapper;
        this.designVersionMapper = designVersionMapper;
        this.designPermissionMapper = designPermissionMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public long insertDocument(
        long tenantId,
        long ownerId,
        long templateId,
        String name,
        int width,
        int height,
        JsonNode templateFieldSnapshot
    ) {
        DesignDocumentMapper.DocumentRow document = new DesignDocumentMapper.DocumentRow();
        document.setTenantId(tenantId);
        document.setOwnerId(ownerId);
        document.setTemplateId(templateId);
        document.setName(name);
        document.setWidth(width);
        document.setHeight(height);
        document.setTemplateFieldSnapshotJson(writeJson(templateFieldSnapshot));
        designDocumentMapper.insert(document);
        if (document.getId() == null) {
            throw new IllegalStateException("Inserted design document has no generated id");
        }
        return document.getId();
    }

    @Override
    public void insertVersion(long tenantId, long documentId, int versionNo, JsonNode schema, long createdBy) {
        DesignVersionMapper.VersionRow version = new DesignVersionMapper.VersionRow();
        version.setTenantId(tenantId);
        version.setDocumentId(documentId);
        version.setVersionNo(versionNo);
        version.setSchemaJson(writeJson(schema));
        version.setCreatedBy(createdBy);
        designVersionMapper.insert(version);
    }

    @Override
    public void insertPermission(long tenantId, long documentId, long userId, String role) {
        designPermissionMapper.insert(tenantId, documentId, userId, role);
    }

    @Override
    public Optional<DesignSnapshot> findSnapshot(long tenantId, long documentId) {
        return Optional.ofNullable(designDocumentMapper.findSnapshot(tenantId, documentId))
            .map(this::toSnapshot);
    }

    @Override
    public boolean canView(long tenantId, long documentId, long userId) {
        return designPermissionMapper.countViewPermission(tenantId, documentId, userId) > 0;
    }

    @Override
    public boolean canEdit(long tenantId, long documentId, long userId) {
        return designPermissionMapper.countEditPermission(tenantId, documentId, userId) > 0;
    }

    @Override
    public boolean advanceVersion(long tenantId, long documentId, int baseVersion) {
        return designDocumentMapper.advanceVersion(tenantId, documentId, baseVersion) == 1;
    }

    @Override
    public List<DesignService.VersionView> findVersions(long tenantId, long documentId) {
        return designVersionMapper.findByDocumentId(tenantId, documentId).stream()
            .map(version -> new DesignService.VersionView(
                version.getId(),
                version.getVersionNo(),
                version.getCreatedBy(),
                version.getCreatedAt().toInstant()
            ))
            .toList();
    }

    private DesignSnapshot toSnapshot(DesignDocumentMapper.SnapshotRow row) {
        return new DesignSnapshot(
            row.getId(),
            row.getTemplateId(),
            row.getName(),
            row.getWidth(),
            row.getHeight(),
            row.getCurrentVersion(),
            readJson(row.getSchemaJson()),
            row.getUpdatedAt().toInstant()
        );
    }

    private String writeJson(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize design JSON", exception);
        }
    }

    private JsonNode readJson(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Persisted design JSON is invalid", exception);
        }
    }
}
