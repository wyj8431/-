package com.example.lowcode.design.infrastructure;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.sql.Timestamp;
import java.util.List;

@Mapper
public interface DesignVersionMapper {
    @Insert("""
        INSERT INTO design_version (document_id, tenant_id, version_no, schema_json, created_by)
        VALUES (#{documentId}, #{tenantId}, #{versionNo}, #{schemaJson}, #{createdBy})
        """)
    int insert(VersionRow version);

    @Select("""
        SELECT id, version_no AS versionNo, created_by AS createdBy, created_at AS createdAt
        FROM design_version
        WHERE tenant_id = #{tenantId} AND document_id = #{documentId}
        ORDER BY version_no
        """)
    List<VersionRow> findByDocumentId(
        @Param("tenantId") long tenantId,
        @Param("documentId") long documentId
    );

    class VersionRow {
        private Long id;
        private long documentId;
        private long tenantId;
        private int versionNo;
        private String schemaJson;
        private long createdBy;
        private Timestamp createdAt;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public long getDocumentId() {
            return documentId;
        }

        public void setDocumentId(long documentId) {
            this.documentId = documentId;
        }

        public long getTenantId() {
            return tenantId;
        }

        public void setTenantId(long tenantId) {
            this.tenantId = tenantId;
        }

        public int getVersionNo() {
            return versionNo;
        }

        public void setVersionNo(int versionNo) {
            this.versionNo = versionNo;
        }

        public String getSchemaJson() {
            return schemaJson;
        }

        public void setSchemaJson(String schemaJson) {
            this.schemaJson = schemaJson;
        }

        public long getCreatedBy() {
            return createdBy;
        }

        public void setCreatedBy(long createdBy) {
            this.createdBy = createdBy;
        }

        public Timestamp getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(Timestamp createdAt) {
            this.createdAt = createdAt;
        }
    }
}
