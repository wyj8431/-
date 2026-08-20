package com.example.lowcode.template.infrastructure;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.sql.Timestamp;
import java.util.List;

@Mapper
public interface TemplateCoverAdminMapper {
    @Insert("""
        INSERT INTO template_cover_upload_session (
            tenant_id, user_id, file_name, mime_type, expected_file_size,
            sha256, object_key, status, expires_at
        ) VALUES (
            #{tenantId}, #{userId}, #{fileName}, #{mimeType}, #{expectedFileSize},
            #{sha256}, #{objectKey}, 'PENDING', #{expiresAt}
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertUploadSession(SessionRow row);

    @Select("""
        SELECT id, tenant_id AS tenantId, user_id AS userId, file_name AS fileName,
               mime_type AS mimeType, expected_file_size AS expectedFileSize,
               sha256, object_key AS objectKey, expires_at AS expiresAt
        FROM template_cover_upload_session
        WHERE id = #{sessionId}
          AND tenant_id = #{tenantId}
          AND user_id = #{userId}
          AND status = 'PENDING'
        """)
    SessionRow findPendingSession(
        @Param("tenantId") long tenantId,
        @Param("userId") long userId,
        @Param("sessionId") long sessionId
    );

    @Update("""
        UPDATE template_cover_upload_session
        SET status = 'COMPLETED', completed_at = #{completedAt}, error_reason = NULL
        WHERE id = #{sessionId} AND status = 'PENDING' AND expires_at > #{completedAt}
        """)
    int markCompleted(@Param("sessionId") long sessionId, @Param("completedAt") Timestamp completedAt);

    @Update("""
        UPDATE template_cover_upload_session
        SET status = 'REJECTED', error_reason = #{reason}
        WHERE id = #{sessionId} AND status = 'PENDING'
        """)
    int markRejected(@Param("sessionId") long sessionId, @Param("reason") String reason);

    @Insert("""
        INSERT INTO template_cover_asset (
            upload_session_id, object_key, mime_type, file_size, width, height, sha256, status
        ) VALUES (
            #{uploadSessionId}, #{objectKey}, #{mimeType}, #{fileSize}, #{width}, #{height}, #{sha256}, 'DRAFT'
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertAsset(AssetRow row);

    @Select({
        "<script>",
        "SELECT id, object_key AS objectKey, mime_type AS mimeType, file_size AS fileSize,",
        "sha256, width, height, status, created_at AS createdAt, updated_at AS updatedAt",
        "FROM template_cover_asset",
        "<where><if test=\"status != null and status != ''\">status = #{status}</if></where>",
        "ORDER BY created_at DESC, id DESC",
        "</script>"
    })
    List<CoverRow> findAll(@Param("status") String status);

    @Select("""
        SELECT id, object_key AS objectKey, mime_type AS mimeType, file_size AS fileSize,
               sha256, width, height, status, created_at AS createdAt, updated_at AS updatedAt
        FROM template_cover_asset
        WHERE id = #{coverAssetId}
        """)
    CoverRow findById(@Param("coverAssetId") long coverAssetId);

    @Update("UPDATE template_cover_asset SET status = #{status} WHERE id = #{coverAssetId}")
    int updateStatus(@Param("coverAssetId") long coverAssetId, @Param("status") String status);

    @Select("""
        SELECT
            (SELECT COUNT(*) FROM design_template WHERE cover_asset_id = #{coverAssetId})
          + (SELECT COUNT(*) FROM home_topic WHERE cover_asset_id = #{coverAssetId})
        """)
    int countReferences(@Param("coverAssetId") long coverAssetId);

    @Delete("DELETE FROM template_cover_asset WHERE id = #{coverAssetId}")
    int deleteById(@Param("coverAssetId") long coverAssetId);

    @Select("SELECT cover_asset_id FROM design_template WHERE id = #{templateId}")
    Long findTemplateCoverId(@Param("templateId") long templateId);

    @Update("UPDATE design_template SET cover_asset_id = #{coverAssetId,jdbcType=BIGINT} WHERE id = #{templateId}")
    int updateTemplateCover(@Param("templateId") long templateId, @Param("coverAssetId") Long coverAssetId);

    class SessionRow {
        private Long id;
        private long tenantId;
        private long userId;
        private String fileName;
        private String mimeType;
        private long expectedFileSize;
        private String sha256;
        private String objectKey;
        private Timestamp expiresAt;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public long getTenantId() { return tenantId; }
        public void setTenantId(long tenantId) { this.tenantId = tenantId; }
        public long getUserId() { return userId; }
        public void setUserId(long userId) { this.userId = userId; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public String getMimeType() { return mimeType; }
        public void setMimeType(String mimeType) { this.mimeType = mimeType; }
        public long getExpectedFileSize() { return expectedFileSize; }
        public void setExpectedFileSize(long expectedFileSize) { this.expectedFileSize = expectedFileSize; }
        public String getSha256() { return sha256; }
        public void setSha256(String sha256) { this.sha256 = sha256; }
        public String getObjectKey() { return objectKey; }
        public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
        public Timestamp getExpiresAt() { return expiresAt; }
        public void setExpiresAt(Timestamp expiresAt) { this.expiresAt = expiresAt; }
    }

    class AssetRow {
        private Long id;
        private long uploadSessionId;
        private String objectKey;
        private String mimeType;
        private long fileSize;
        private String sha256;
        private int width;
        private int height;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public long getUploadSessionId() { return uploadSessionId; }
        public void setUploadSessionId(long uploadSessionId) { this.uploadSessionId = uploadSessionId; }
        public String getObjectKey() { return objectKey; }
        public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
        public String getMimeType() { return mimeType; }
        public void setMimeType(String mimeType) { this.mimeType = mimeType; }
        public long getFileSize() { return fileSize; }
        public void setFileSize(long fileSize) { this.fileSize = fileSize; }
        public String getSha256() { return sha256; }
        public void setSha256(String sha256) { this.sha256 = sha256; }
        public int getWidth() { return width; }
        public void setWidth(int width) { this.width = width; }
        public int getHeight() { return height; }
        public void setHeight(int height) { this.height = height; }
    }

    class CoverRow {
        private long id;
        private String objectKey;
        private String mimeType;
        private long fileSize;
        private String sha256;
        private int width;
        private int height;
        private String status;
        private Timestamp createdAt;
        private Timestamp updatedAt;
        public long getId() { return id; }
        public void setId(long id) { this.id = id; }
        public String getObjectKey() { return objectKey; }
        public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
        public String getMimeType() { return mimeType; }
        public void setMimeType(String mimeType) { this.mimeType = mimeType; }
        public long getFileSize() { return fileSize; }
        public void setFileSize(long fileSize) { this.fileSize = fileSize; }
        public String getSha256() { return sha256; }
        public void setSha256(String sha256) { this.sha256 = sha256; }
        public int getWidth() { return width; }
        public void setWidth(int width) { this.width = width; }
        public int getHeight() { return height; }
        public void setHeight(int height) { this.height = height; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Timestamp getCreatedAt() { return createdAt; }
        public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
        public Timestamp getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
    }
}
