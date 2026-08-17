CREATE TABLE sys_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    phone VARCHAR(32) NOT NULL,
    wechat_open_id VARCHAR(128) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_phone (phone),
    UNIQUE KEY uk_user_wechat_open_id (wechat_open_id),
    CONSTRAINT chk_user_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE TABLE sys_tenant (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT chk_tenant_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE TABLE sys_tenant_member (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_user (tenant_id, user_id),
    CONSTRAINT fk_tenant_member_tenant FOREIGN KEY (tenant_id) REFERENCES sys_tenant (id),
    CONSTRAINT fk_tenant_member_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT chk_tenant_member_role CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER'))
);

CREATE TABLE design_template (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    width INT NOT NULL,
    height INT NOT NULL,
    cover_asset_id BIGINT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    schema_json JSON NOT NULL,
    published_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_template_status_published (status, published_at),
    CONSTRAINT chk_template_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'DISABLED')),
    CONSTRAINT chk_template_dimensions CHECK (width > 0 AND width <= 10000 AND height > 0 AND height <= 10000)
);

CREATE TABLE template_field (
    id BIGINT NOT NULL AUTO_INCREMENT,
    template_id BIGINT NOT NULL,
    field_key VARCHAR(64) NOT NULL,
    element_id VARCHAR(64) NOT NULL,
    label VARCHAR(128) NOT NULL,
    field_type VARCHAR(16) NOT NULL,
    is_required TINYINT NOT NULL,
    default_value VARCHAR(1024) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_template_field (template_id, field_key),
    UNIQUE KEY uk_template_element (template_id, element_id),
    CONSTRAINT fk_template_field_template FOREIGN KEY (template_id) REFERENCES design_template (id),
    CONSTRAINT chk_template_field_type CHECK (field_type IN ('TEXT', 'IMAGE')),
    CONSTRAINT chk_template_field_required CHECK (is_required IN (0, 1))
);

CREATE TABLE design_document (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    owner_id BIGINT NOT NULL,
    template_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    width INT NOT NULL,
    height INT NOT NULL,
    template_field_snapshot_json JSON NOT NULL,
    current_version INT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_document_id_tenant (id, tenant_id),
    KEY idx_design_tenant_updated (tenant_id, updated_at),
    KEY idx_design_owner_updated (owner_id, updated_at),
    CONSTRAINT fk_document_template FOREIGN KEY (template_id) REFERENCES design_template (id),
    CONSTRAINT fk_document_tenant_owner FOREIGN KEY (tenant_id, owner_id)
        REFERENCES sys_tenant_member (tenant_id, user_id),
    CONSTRAINT chk_document_status CHECK (status IN ('DRAFT', 'ACTIVE', 'DELETED')),
    CONSTRAINT chk_document_dimensions CHECK (width > 0 AND width <= 10000 AND height > 0 AND height <= 10000),
    CONSTRAINT chk_document_version CHECK (current_version >= 1)
);

CREATE TABLE design_version (
    id BIGINT NOT NULL AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    schema_json JSON NOT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_document_version (document_id, version_no),
    KEY idx_design_version_tenant_document (tenant_id, document_id),
    CONSTRAINT fk_version_document_tenant FOREIGN KEY (document_id, tenant_id)
        REFERENCES design_document (id, tenant_id),
    CONSTRAINT fk_version_tenant_member FOREIGN KEY (tenant_id, created_by)
        REFERENCES sys_tenant_member (tenant_id, user_id),
    CONSTRAINT chk_design_version_no CHECK (version_no >= 1)
);

CREATE TABLE design_permission (
    id BIGINT NOT NULL AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_document_user_permission (document_id, user_id),
    KEY idx_design_permission_tenant_user (tenant_id, user_id),
    CONSTRAINT fk_permission_document_tenant FOREIGN KEY (document_id, tenant_id)
        REFERENCES design_document (id, tenant_id),
    CONSTRAINT fk_permission_tenant_member FOREIGN KEY (tenant_id, user_id)
        REFERENCES sys_tenant_member (tenant_id, user_id),
    CONSTRAINT chk_design_permission_role CHECK (role IN ('OWNER', 'EDITOR', 'VIEWER'))
);

CREATE TABLE asset_upload_session (
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
    UNIQUE KEY uk_upload_session_object_key (object_key),
    KEY idx_upload_status_expiry (status, expires_at),
    CONSTRAINT fk_upload_session_tenant_member FOREIGN KEY (tenant_id, user_id)
        REFERENCES sys_tenant_member (tenant_id, user_id),
    CONSTRAINT chk_upload_session_status CHECK (status IN ('PENDING', 'COMPLETED', 'REJECTED', 'EXPIRED')),
    CONSTRAINT chk_upload_session_mime CHECK (mime_type IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT chk_upload_session_size CHECK (expected_file_size > 0 AND expected_file_size <= 10485760),
    CONSTRAINT chk_upload_session_sha CHECK (CHAR_LENGTH(sha256) = 64)
);

CREATE TABLE asset (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    owner_id BIGINT NOT NULL,
    upload_session_id BIGINT NOT NULL,
    object_key VARCHAR(512) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(32) NOT NULL,
    file_size BIGINT NOT NULL,
    sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    width INT NOT NULL,
    height INT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_asset_object_key (object_key),
    UNIQUE KEY uk_asset_upload_session (upload_session_id),
    KEY idx_asset_tenant_created (tenant_id, created_at),
    CONSTRAINT fk_asset_tenant_owner FOREIGN KEY (tenant_id, owner_id)
        REFERENCES sys_tenant_member (tenant_id, user_id),
    CONSTRAINT fk_asset_upload_session FOREIGN KEY (upload_session_id) REFERENCES asset_upload_session (id),
    CONSTRAINT chk_asset_mime CHECK (mime_type IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT chk_asset_size CHECK (file_size > 0 AND file_size <= 10485760),
    CONSTRAINT chk_asset_sha CHECK (CHAR_LENGTH(sha256) = 64),
    CONSTRAINT chk_asset_dimensions CHECK (width > 0 AND width <= 20000 AND height > 0 AND height <= 20000)
);

INSERT INTO design_template (id, name, width, height, status, schema_json, published_at)
VALUES (
    1001,
    '朋友圈促销',
    1080,
    1440,
    'PUBLISHED',
    '{
      "schemaVersion": 1,
      "canvas": {"width": 1080, "height": 1440, "background": "#ffffff"},
      "pages": [{
        "id": "page-1",
        "elements": [
          {
            "id": "text-product-name",
            "type": "text",
            "transform": {"x": 96, "y": 180, "width": 888, "height": 96, "rotate": 0},
            "props": {"text": "商品名称", "fontSize": 48, "color": "#1f2937"},
            "visible": true,
            "locked": false,
            "zIndex": 1
          },
          {
            "id": "image-product-image",
            "type": "image",
            "transform": {"x": 96, "y": 320, "width": 888, "height": 888, "rotate": 0},
            "props": {"src": ""},
            "visible": true,
            "locked": false,
            "zIndex": 2
          }
        ]
      }]
    }',
    CURRENT_TIMESTAMP(6)
);

INSERT INTO template_field (template_id, field_key, element_id, label, field_type, is_required, default_value)
VALUES
    (1001, 'productName', 'text-product-name', '商品名称', 'TEXT', 1, ''),
    (1001, 'productImage', 'image-product-image', '商品图片', 'IMAGE', 1, '');
