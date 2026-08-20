ALTER TABLE home_topic_template
    DROP FOREIGN KEY fk_home_topic_template_topic;

ALTER TABLE home_topic
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;

ALTER TABLE home_topic_template
    ADD CONSTRAINT fk_home_topic_template_topic
        FOREIGN KEY (topic_id) REFERENCES home_topic (id);
