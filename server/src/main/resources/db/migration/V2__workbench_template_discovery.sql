CREATE TABLE template_category (
    id BIGINT NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    parent_id BIGINT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_template_category_code (code),
    KEY idx_template_category_parent (parent_id, sort_order, id),
    CONSTRAINT fk_template_category_parent FOREIGN KEY (parent_id) REFERENCES template_category (id),
    CONSTRAINT chk_template_category_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'DISABLED'))
);

CREATE TABLE template_tag (
    id BIGINT NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_template_tag_code (code),
    KEY idx_template_tag_public (status, sort_order, id),
    CONSTRAINT chk_template_tag_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'DISABLED'))
);

CREATE TABLE template_cover_asset (
    id BIGINT NOT NULL AUTO_INCREMENT,
    object_key VARCHAR(512) NULL,
    mime_type VARCHAR(32) NOT NULL,
    file_size BIGINT NOT NULL,
    width INT NOT NULL,
    height INT NOT NULL,
    sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_template_cover_asset_object_key (object_key),
    KEY idx_template_cover_asset_status (status),
    CONSTRAINT chk_template_cover_asset_mime CHECK (mime_type IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT chk_template_cover_asset_size CHECK (file_size > 0 AND file_size <= 10485760),
    CONSTRAINT chk_template_cover_asset_sha CHECK (CHAR_LENGTH(sha256) = 64),
    CONSTRAINT chk_template_cover_asset_dimensions CHECK (width > 0 AND width <= 20000 AND height > 0 AND height <= 20000),
    CONSTRAINT chk_template_cover_asset_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'DISABLED'))
);

CREATE TABLE home_topic (
    id BIGINT NOT NULL,
    code VARCHAR(64) NOT NULL,
    title VARCHAR(128) NOT NULL,
    subtitle VARCHAR(255) NULL,
    type VARCHAR(32) NOT NULL,
    cover_asset_id BIGINT NULL,
    starts_at DATETIME(6) NULL,
    ends_at DATETIME(6) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_home_topic_code (code),
    KEY idx_home_topic_public (status, type, starts_at, ends_at, sort_order, id),
    CONSTRAINT fk_home_topic_cover_asset FOREIGN KEY (cover_asset_id) REFERENCES template_cover_asset (id),
    CONSTRAINT chk_home_topic_type CHECK (type IN ('HOTSPOT_CALENDAR', 'EDITORIAL_SCENE')),
    CONSTRAINT chk_home_topic_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'DISABLED')),
    CONSTRAINT chk_home_topic_time_range CHECK (ends_at IS NULL OR starts_at IS NULL OR ends_at > starts_at)
);

ALTER TABLE design_template
    ADD COLUMN category_id BIGINT NULL AFTER id,
    ADD COLUMN featured_rank INT NULL AFTER cover_asset_id,
    ADD KEY idx_template_discovery (status, category_id, published_at, id),
    ADD KEY idx_template_featured (status, featured_rank, published_at, id),
    ADD CONSTRAINT fk_template_category FOREIGN KEY (category_id) REFERENCES template_category (id),
    ADD CONSTRAINT fk_template_cover_asset FOREIGN KEY (cover_asset_id) REFERENCES template_cover_asset (id);

CREATE TABLE template_tag_relation (
    template_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (template_id, tag_id),
    UNIQUE KEY uk_template_tag_relation (template_id, tag_id),
    KEY idx_template_tag_relation_tag (tag_id, template_id),
    CONSTRAINT fk_template_tag_relation_template FOREIGN KEY (template_id) REFERENCES design_template (id),
    CONSTRAINT fk_template_tag_relation_tag FOREIGN KEY (tag_id) REFERENCES template_tag (id)
);

CREATE TABLE home_topic_template (
    topic_id BIGINT NOT NULL,
    template_id BIGINT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (topic_id, template_id),
    UNIQUE KEY uk_home_topic_template (topic_id, template_id),
    KEY idx_home_topic_template_template (template_id, topic_id),
    CONSTRAINT fk_home_topic_template_topic FOREIGN KEY (topic_id) REFERENCES home_topic (id),
    CONSTRAINT fk_home_topic_template_template FOREIGN KEY (template_id) REFERENCES design_template (id)
);

INSERT INTO template_category (id, code, name, sort_order, status)
VALUES (10, 'marketing', '营销推广', 10, 'PUBLISHED');

INSERT INTO template_tag (id, code, name, sort_order, status)
VALUES (20, 'promotion', '促销', 10, 'PUBLISHED');

UPDATE design_template
SET category_id = 10,
    featured_rank = 10
WHERE id = 1001;

INSERT INTO template_tag_relation (template_id, tag_id)
VALUES (1001, 20);

INSERT INTO home_topic (id, code, title, subtitle, type, sort_order, status)
VALUES (30, 'summer-promotion', '夏日促销', '夏季营销模板精选', 'EDITORIAL_SCENE', 10, 'PUBLISHED');

INSERT INTO home_topic_template (topic_id, template_id, sort_order)
VALUES (30, 1001, 10);
