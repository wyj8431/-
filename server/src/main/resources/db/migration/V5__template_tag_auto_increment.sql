ALTER TABLE template_tag_relation
    DROP FOREIGN KEY fk_template_tag_relation_tag;

ALTER TABLE template_tag
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;

ALTER TABLE template_tag_relation
    ADD CONSTRAINT fk_template_tag_relation_tag
        FOREIGN KEY (tag_id) REFERENCES template_tag (id);
