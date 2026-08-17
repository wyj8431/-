package com.example.lowcode.asset.infrastructure;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.sql.Timestamp;

@Mapper
public interface AssetUploadSessionMapper {
    @Insert("""
        INSERT INTO asset_upload_session (
            tenant_id, user_id, file_name, mime_type, expected_file_size,
            sha256, object_key, status, expires_at
        ) VALUES (
            #{tenantId}, #{userId}, #{fileName}, #{mimeType}, #{expectedFileSize},
            #{sha256}, #{objectKey}, 'PENDING', #{expiresAt}
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(SessionRow session);

    @Select("""
        SELECT id, tenant_id AS tenantId, user_id AS userId, file_name AS fileName,
               mime_type AS mimeType, expected_file_size AS expectedFileSize,
               sha256, object_key AS objectKey, expires_at AS expiresAt
        FROM asset_upload_session
        WHERE id = #{sessionId}
          AND tenant_id = #{tenantId}
          AND user_id = #{userId}
          AND status = 'PENDING'
        """)
    SessionRow findPending(
        @Param("tenantId") long tenantId,
        @Param("userId") long userId,
        @Param("sessionId") long sessionId
    );

    @Update("""
        UPDATE asset_upload_session
        SET status = 'COMPLETED', completed_at = #{completedAt}, error_reason = NULL
        WHERE id = #{sessionId}
          AND tenant_id = #{tenantId}
          AND status = 'PENDING'
          AND expires_at > #{completedAt}
        """)
    int markCompleted(
        @Param("tenantId") long tenantId,
        @Param("sessionId") long sessionId,
        @Param("completedAt") Timestamp completedAt
    );

    @Update("""
        UPDATE asset_upload_session
        SET status = 'REJECTED', error_reason = #{reason}
        WHERE id = #{sessionId}
          AND tenant_id = #{tenantId}
          AND status = 'PENDING'
        """)
    int markRejected(
        @Param("tenantId") long tenantId,
        @Param("sessionId") long sessionId,
        @Param("reason") String reason
    );

    @Update("""
        UPDATE asset_upload_session
        SET status = 'EXPIRED', error_reason = '上传会话已过期'
        WHERE id = #{sessionId}
          AND tenant_id = #{tenantId}
          AND status = 'PENDING'
          AND expires_at <= #{now}
        """)
    int markExpired(
        @Param("tenantId") long tenantId,
        @Param("sessionId") long sessionId,
        @Param("now") Timestamp now
    );

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

        public long getUserId() {
            return userId;
        }

        public void setUserId(long userId) {
            this.userId = userId;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public String getMimeType() {
            return mimeType;
        }

        public void setMimeType(String mimeType) {
            this.mimeType = mimeType;
        }

        public long getExpectedFileSize() {
            return expectedFileSize;
        }

        public void setExpectedFileSize(long expectedFileSize) {
            this.expectedFileSize = expectedFileSize;
        }

        public String getSha256() {
            return sha256;
        }

        public void setSha256(String sha256) {
            this.sha256 = sha256;
        }

        public String getObjectKey() {
            return objectKey;
        }

        public void setObjectKey(String objectKey) {
            this.objectKey = objectKey;
        }

        public Timestamp getExpiresAt() {
            return expiresAt;
        }

        public void setExpiresAt(Timestamp expiresAt) {
            this.expiresAt = expiresAt;
        }
    }
}
