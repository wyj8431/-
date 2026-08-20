CREATE TABLE auth_wechat_oauth_state (
    id BIGINT NOT NULL AUTO_INCREMENT,
    state_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    purpose VARCHAR(16) NOT NULL,
    initiator_user_id BIGINT NULL,
    initiator_tenant_id BIGINT NULL,
    return_path VARCHAR(512) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    consumed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_wechat_oauth_state_hash (state_hash),
    KEY idx_wechat_oauth_state_expiry (expires_at, consumed_at),
    CONSTRAINT fk_wechat_oauth_state_initiator_member
        FOREIGN KEY (initiator_tenant_id, initiator_user_id)
        REFERENCES sys_tenant_member (tenant_id, user_id),
    CONSTRAINT chk_wechat_oauth_state_purpose CHECK (purpose IN ('LOGIN', 'BIND')),
    CONSTRAINT chk_wechat_oauth_state_initiator CHECK (
        (purpose = 'LOGIN' AND initiator_user_id IS NULL AND initiator_tenant_id IS NULL)
        OR (purpose = 'BIND' AND initiator_user_id IS NOT NULL AND initiator_tenant_id IS NOT NULL)
    )
);
