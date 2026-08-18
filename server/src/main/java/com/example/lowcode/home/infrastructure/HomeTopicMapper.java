package com.example.lowcode.home.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.List;

@Mapper
public interface HomeTopicMapper {
    @Select("""
        SELECT id, code, title, subtitle, type, cover_asset_id AS coverAssetId,
               starts_at AS startsAt, ends_at AS endsAt
        FROM home_topic
        WHERE status = 'PUBLISHED'
        ORDER BY sort_order ASC, id ASC
        """)
    List<TopicRow> findPublishedTopics();

    @Select("""
        SELECT template_id
        FROM home_topic_template
        WHERE topic_id = #{topicId}
        ORDER BY sort_order ASC, template_id ASC
        """)
    List<Long> findTemplateIds(@Param("topicId") long topicId);

    class TopicRow {
        private long id;
        private String code;
        private String title;
        private String subtitle;
        private String type;
        private Long coverAssetId;
        private Instant startsAt;
        private Instant endsAt;

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getSubtitle() {
            return subtitle;
        }

        public void setSubtitle(String subtitle) {
            this.subtitle = subtitle;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Long getCoverAssetId() {
            return coverAssetId;
        }

        public void setCoverAssetId(Long coverAssetId) {
            this.coverAssetId = coverAssetId;
        }

        public Instant getStartsAt() {
            return startsAt;
        }

        public void setStartsAt(Instant startsAt) {
            this.startsAt = startsAt;
        }

        public Instant getEndsAt() {
            return endsAt;
        }

        public void setEndsAt(Instant endsAt) {
            this.endsAt = endsAt;
        }
    }
}
