package com.example.lowcode.design.infrastructure;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.sql.Timestamp;

@Mapper
public interface DesignDocumentMapper {
    @Insert("""
        INSERT INTO design_document (
            tenant_id, owner_id, template_id, name, width, height,
            template_field_snapshot_json, current_version, status
        ) VALUES (
            #{tenantId}, #{ownerId}, #{templateId}, #{name}, #{width}, #{height},
            #{templateFieldSnapshotJson}, 1, 'ACTIVE'
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(DocumentRow document);

    @Select("""
        SELECT d.id, d.template_id AS templateId, d.name, d.width, d.height,
               d.current_version AS currentVersion, v.schema_json AS schemaJson,
               d.updated_at AS updatedAt
        FROM design_document d
        INNER JOIN design_version v
            ON v.document_id = d.id
           AND v.tenant_id = d.tenant_id
           AND v.version_no = d.current_version
        WHERE d.id = #{documentId}
          AND d.tenant_id = #{tenantId}
          AND d.status <> 'DELETED'
        """)
    SnapshotRow findSnapshot(
        @Param("tenantId") long tenantId,
        @Param("documentId") long documentId
    );

    @Update("""
        UPDATE design_document
        SET current_version = current_version + 1,
            updated_at = CURRENT_TIMESTAMP(6)
        WHERE id = #{documentId}
          AND tenant_id = #{tenantId}
          AND current_version = #{baseVersion}
          AND status <> 'DELETED'
        """)
    int advanceVersion(
        @Param("tenantId") long tenantId,
        @Param("documentId") long documentId,
        @Param("baseVersion") int baseVersion
    );

    class DocumentRow {
        private Long id;
        private long tenantId;
        private long ownerId;
        private long templateId;
        private String name;
        private int width;
        private int height;
        private String templateFieldSnapshotJson;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public long getTenantId() {
            return tenantId;
        }

        public void setTenantId(long tenantId) {
            this.tenantId = tenantId;
        }

        public long getOwnerId() {
            return ownerId;
        }

        public void setOwnerId(long ownerId) {
            this.ownerId = ownerId;
        }

        public long getTemplateId() {
            return templateId;
        }

        public void setTemplateId(long templateId) {
            this.templateId = templateId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getWidth() {
            return width;
        }

        public void setWidth(int width) {
            this.width = width;
        }

        public int getHeight() {
            return height;
        }

        public void setHeight(int height) {
            this.height = height;
        }

        public String getTemplateFieldSnapshotJson() {
            return templateFieldSnapshotJson;
        }

        public void setTemplateFieldSnapshotJson(String templateFieldSnapshotJson) {
            this.templateFieldSnapshotJson = templateFieldSnapshotJson;
        }
    }

    class SnapshotRow {
        private long id;
        private long templateId;
        private String name;
        private int width;
        private int height;
        private int currentVersion;
        private String schemaJson;
        private Timestamp updatedAt;

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public long getTemplateId() {
            return templateId;
        }

        public void setTemplateId(long templateId) {
            this.templateId = templateId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getWidth() {
            return width;
        }

        public void setWidth(int width) {
            this.width = width;
        }

        public int getHeight() {
            return height;
        }

        public void setHeight(int height) {
            this.height = height;
        }

        public int getCurrentVersion() {
            return currentVersion;
        }

        public void setCurrentVersion(int currentVersion) {
            this.currentVersion = currentVersion;
        }

        public String getSchemaJson() {
            return schemaJson;
        }

        public void setSchemaJson(String schemaJson) {
            this.schemaJson = schemaJson;
        }

        public Timestamp getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(Timestamp updatedAt) {
            this.updatedAt = updatedAt;
        }
    }
}
