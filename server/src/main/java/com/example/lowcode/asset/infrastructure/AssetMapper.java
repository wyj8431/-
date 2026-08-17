package com.example.lowcode.asset.infrastructure;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

@Mapper
public interface AssetMapper {
    @Insert("""
        INSERT INTO asset (
            tenant_id, owner_id, upload_session_id, object_key, file_name,
            mime_type, file_size, sha256, width, height
        ) VALUES (
            #{tenantId}, #{ownerId}, #{uploadSessionId}, #{objectKey}, #{fileName},
            #{mimeType}, #{fileSize}, #{sha256}, #{width}, #{height}
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(AssetRow asset);

    class AssetRow {
        private Long id;
        private long tenantId;
        private long ownerId;
        private long uploadSessionId;
        private String objectKey;
        private String fileName;
        private String mimeType;
        private long fileSize;
        private String sha256;
        private int width;
        private int height;

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

        public long getUploadSessionId() {
            return uploadSessionId;
        }

        public void setUploadSessionId(long uploadSessionId) {
            this.uploadSessionId = uploadSessionId;
        }

        public String getObjectKey() {
            return objectKey;
        }

        public void setObjectKey(String objectKey) {
            this.objectKey = objectKey;
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

        public long getFileSize() {
            return fileSize;
        }

        public void setFileSize(long fileSize) {
            this.fileSize = fileSize;
        }

        public String getSha256() {
            return sha256;
        }

        public void setSha256(String sha256) {
            this.sha256 = sha256;
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
    }
}
