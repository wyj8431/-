ALTER TABLE sys_user
    ADD COLUMN security_version INT NOT NULL DEFAULT 0 AFTER status,
    ADD CONSTRAINT chk_user_security_version CHECK (security_version >= 0);

CREATE TABLE auth_refresh_token (
    id BIGINT NOT NULL AUTO_INCREMENT,
    token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    family_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    security_version INT NOT NULL,
    replaced_by_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    expires_at DATETIME(6) NOT NULL,
    last_used_at DATETIME(6) NULL,
    revoked_at DATETIME(6) NULL,
    revoke_reason VARCHAR(64) NULL,
    user_agent VARCHAR(512) NULL,
    ip_address VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_token_hash (token_hash),
    KEY idx_refresh_family (family_id),
    KEY idx_refresh_user_active (user_id, revoked_at, expires_at),
    CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_refresh_tenant_member FOREIGN KEY (tenant_id, user_id)
        REFERENCES sys_tenant_member (tenant_id, user_id),
    CONSTRAINT chk_refresh_security_version CHECK (security_version >= 0)
);

CREATE TABLE sys_audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    actor_user_id BIGINT NULL,
    tenant_id BIGINT NULL,
    action VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(128) NULL,
    outcome VARCHAR(16) NOT NULL,
    request_id VARCHAR(64) NULL,
    metadata_json JSON NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_audit_created (created_at),
    KEY idx_audit_actor_created (actor_user_id, created_at),
    KEY idx_audit_action_created (action, created_at),
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_user_id) REFERENCES sys_user (id),
    CONSTRAINT chk_audit_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE'))
);
