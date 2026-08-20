package com.example.lowcode.home.infrastructure;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.sql.Timestamp;
import java.util.List;

@Mapper
public interface HomeTopicAdminMapper {
    @Select({
        "<script>",
        "SELECT h.id, h.code, h.title, h.subtitle, h.type, h.cover_asset_id AS coverAssetId,",
        "h.starts_at AS startsAt, h.ends_at AS endsAt, h.sort_order AS sortOrder, h.status,",
        "(SELECT COUNT(*) FROM home_topic_template r WHERE r.topic_id = h.id) AS templateCount",
        "FROM home_topic h",
        "<where><if test=\"status != null and status != ''\">h.status = #{status}</if></where>",
        "ORDER BY h.sort_order ASC, h.id ASC",
        "</script>"
    })
    List<TopicRow> findAll(@Param("status") String status);

    @Select("""
        SELECT id, code, title, subtitle, type, cover_asset_id AS coverAssetId,
               starts_at AS startsAt, ends_at AS endsAt, sort_order AS sortOrder, status
        FROM home_topic WHERE id = #{id}
        """)
    TopicRow findById(@Param("id") long id);

    @Select("""
        SELECT id, code, title, subtitle, type, cover_asset_id AS coverAssetId,
               starts_at AS startsAt, ends_at AS endsAt, sort_order AS sortOrder, status
        FROM home_topic WHERE code = #{code}
        """)
    TopicRow findByCode(@Param("code") String code);

    @Insert("""
        INSERT INTO home_topic (code, title, subtitle, type, cover_asset_id, starts_at, ends_at, sort_order, status)
        VALUES (#{code}, #{title}, #{subtitle}, #{type}, #{coverAssetId}, #{startsAt}, #{endsAt}, #{sortOrder}, #{status})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(TopicRow row);

    @Update("""
        UPDATE home_topic
        SET code = #{code}, title = #{title}, subtitle = #{subtitle}, type = #{type},
            cover_asset_id = #{coverAssetId}, starts_at = #{startsAt}, ends_at = #{endsAt},
            sort_order = #{sortOrder}
        WHERE id = #{id}
        """)
    int update(TopicRow row);

    @Update("UPDATE home_topic SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") long id, @Param("status") String status);

    @Delete("DELETE FROM home_topic WHERE id = #{id}")
    int delete(@Param("id") long id);

    @Delete("DELETE FROM home_topic_template WHERE topic_id = #{topicId}")
    int deleteTemplateRelations(@Param("topicId") long topicId);

    @Insert("INSERT INTO home_topic_template (topic_id, template_id, sort_order) VALUES (#{topicId}, #{templateId}, #{sortOrder})")
    int insertTemplateRelation(@Param("topicId") long topicId, @Param("templateId") long templateId, @Param("sortOrder") int sortOrder);

    @Select("""
        <script>
        SELECT id FROM design_template WHERE id IN
        <foreach collection="templateIds" item="id" open="(" separator="," close=")">#{id}</foreach>
        </script>
        """)
    List<Long> findExistingTemplateIds(@Param("templateIds") List<Long> templateIds);

    @Select("SELECT template_id FROM home_topic_template WHERE topic_id = #{topicId} ORDER BY sort_order ASC, template_id ASC")
    List<Long> findTemplateIds(@Param("topicId") long topicId);

    class TopicRow {
        private Long id;
        private String code;
        private String title;
        private String subtitle;
        private String type;
        private Long coverAssetId;
        private Timestamp startsAt;
        private Timestamp endsAt;
        private int sortOrder;
        private String status;
        private int templateCount;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getSubtitle() { return subtitle; }
        public void setSubtitle(String subtitle) { this.subtitle = subtitle; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public Long getCoverAssetId() { return coverAssetId; }
        public void setCoverAssetId(Long coverAssetId) { this.coverAssetId = coverAssetId; }
        public Timestamp getStartsAt() { return startsAt; }
        public void setStartsAt(Timestamp startsAt) { this.startsAt = startsAt; }
        public Timestamp getEndsAt() { return endsAt; }
        public void setEndsAt(Timestamp endsAt) { this.endsAt = endsAt; }
        public int getSortOrder() { return sortOrder; }
        public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public int getTemplateCount() { return templateCount; }
        public void setTemplateCount(int templateCount) { this.templateCount = templateCount; }
    }
}
