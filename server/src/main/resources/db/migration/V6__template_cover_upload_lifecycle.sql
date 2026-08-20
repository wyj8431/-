CREATE TABLE template_cover_upload_session (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(32) NOT NULL,
    expected_file_size BIGINT NOT NULL,
    sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    object_key VARCHAR(512) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    error_reason VARCHAR(255) NULL,
    expires_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_template_cover_upload_object_key (object_key),
    KEY idx_template_cover_upload_status_expiry (status, expires_at),
    CONSTRAINT fk_template_cover_upload_tenant_member FOREIGN KEY (tenant_id, user_id)
        REFERENCES sys_tenant_member (tenant_id, user_id),
    CONSTRAINT chk_template_cover_upload_status CHECK (status IN ('PENDING', 'COMPLETED', 'REJECTED', 'EXPIRED')),
    CONSTRAINT chk_template_cover_upload_mime CHECK (mime_type IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT chk_template_cover_upload_size CHECK (expected_file_size > 0 AND expected_file_size <= 10485760),
    CONSTRAINT chk_template_cover_upload_sha CHECK (CHAR_LENGTH(sha256) = 64)
);

ALTER TABLE template_cover_asset
    ADD COLUMN upload_session_id BIGINT NULL AFTER id,
    ADD UNIQUE KEY uk_template_cover_asset_upload_session (upload_session_id),
    ADD CONSTRAINT fk_template_cover_asset_upload_session
        FOREIGN KEY (upload_session_id) REFERENCES template_cover_upload_session (id);
