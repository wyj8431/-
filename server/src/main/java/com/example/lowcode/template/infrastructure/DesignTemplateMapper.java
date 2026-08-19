package com.example.lowcode.template.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.Map;
import java.util.List;

@Mapper
public interface DesignTemplateMapper {
    @SelectProvider(type = SearchSqlProvider.class, method = "search")
    List<TemplateRow> searchPublished(
        @Param("keyword") String keyword,
        @Param("categoryCode") String categoryCode,
        @Param("tagCode") String tagCode,
        @Param("limit") int limit,
        @Param("offset") long offset
    );

    @SelectProvider(type = SearchSqlProvider.class, method = "count")
    long countPublished(
        @Param("keyword") String keyword,
        @Param("categoryCode") String categoryCode,
        @Param("tagCode") String tagCode
    );

    @Select({
        "<script>",
        "SELECT t.id, t.name, t.width, t.height,",
        "t.cover_asset_id AS coverAssetId, c.code AS categoryCode, t.published_at AS publishedAt",
        "FROM design_template t",
        "LEFT JOIN template_category c ON c.id = t.category_id AND c.status = 'PUBLISHED'",
        "WHERE t.status = 'PUBLISHED' AND t.id IN",
        "<foreach collection='templateIds' item='templateId' open='(' separator=',' close=')'>",
        "#{templateId}",
        "</foreach>",
        "ORDER BY t.featured_rank IS NULL ASC, t.featured_rank ASC, t.published_at DESC, t.id DESC",
        "</script>"
    })
    List<TemplateRow> findPublishedByIds(@Param("templateIds") List<Long> templateIds);

    @Select("""
        SELECT id, name, width, height, cover_asset_id AS coverAssetId, schema_json AS schemaJson
        FROM design_template
        WHERE id = #{templateId} AND status = 'PUBLISHED'
        """)
    TemplateRow findPublishedById(@Param("templateId") long templateId);

    @Select({
        "<script>",
        "SELECT t.id, t.name, t.width, t.height, t.cover_asset_id AS coverAssetId,",
        "c.code AS categoryCode, t.featured_rank AS featuredRank, t.status,",
        "t.published_at AS publishedAt, t.updated_at AS updatedAt",
        "FROM design_template t",
        "LEFT JOIN template_category c ON c.id = t.category_id",
        "<where>",
        "<if test=\"status != null and status != ''\">t.status = #{status}</if>",
        "</where>",
        "ORDER BY CASE t.status WHEN 'DRAFT' THEN 0 WHEN 'PUBLISHED' THEN 1 ELSE 2 END,",
        "t.featured_rank IS NULL ASC, t.featured_rank ASC, t.id DESC",
        "</script>"
    })
    List<AdminTemplateRow> findAdmin(@Param("status") String status);

    @Select("""
        SELECT t.id, t.name, t.width, t.height, t.cover_asset_id AS coverAssetId,
               c.code AS categoryCode, t.featured_rank AS featuredRank, t.status,
               t.published_at AS publishedAt, t.updated_at AS updatedAt
        FROM design_template t
        LEFT JOIN template_category c ON c.id = t.category_id
        WHERE t.id = #{templateId}
        """)
    AdminTemplateRow findAdminById(@Param("templateId") long templateId);

    @Update("""
        UPDATE design_template
        SET status = #{status}, published_at = #{publishedAt,jdbcType=TIMESTAMP}
        WHERE id = #{templateId}
        """)
    int updateStatus(
        @Param("templateId") long templateId,
        @Param("status") String status,
        @Param("publishedAt") Instant publishedAt
    );

    @Insert("""
        INSERT INTO design_template
            (category_id, name, width, height, cover_asset_id, featured_rank, status, schema_json)
        VALUES
            (#{categoryId,jdbcType=BIGINT}, #{name}, #{width}, #{height},
             #{coverAssetId,jdbcType=BIGINT}, #{featuredRank,jdbcType=INTEGER}, #{status},
             CAST(#{schemaJson} AS JSON))
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(AdminTemplateRow row);

    @Update("""
        UPDATE design_template
        SET name = #{name}, width = #{width}, height = #{height},
            category_id = #{categoryId,jdbcType=BIGINT},
            cover_asset_id = #{coverAssetId,jdbcType=BIGINT},
            featured_rank = #{featuredRank,jdbcType=INTEGER}
        WHERE id = #{templateId}
        """)
    int updateDetails(
        @Param("templateId") long templateId,
        @Param("name") String name,
        @Param("width") int width,
        @Param("height") int height,
        @Param("categoryId") Long categoryId,
        @Param("coverAssetId") Long coverAssetId,
        @Param("featuredRank") Integer featuredRank
    );

    @Select("""
        SELECT
            (SELECT COUNT(*) FROM design_document WHERE template_id = #{templateId})
          + (SELECT COUNT(*) FROM template_field WHERE template_id = #{templateId})
          + (SELECT COUNT(*) FROM template_tag_relation WHERE template_id = #{templateId})
          + (SELECT COUNT(*) FROM home_topic_template WHERE template_id = #{templateId})
        """)
    int countReferences(@Param("templateId") long templateId);

    @Delete("DELETE FROM design_template WHERE id = #{templateId}")
    int deleteById(@Param("templateId") long templateId);

    class SearchSqlProvider {
        public String search(Map<String, Object> parameters) {
            return "<script>"
                + "SELECT t.id, t.name, t.width, t.height, "
                + "t.cover_asset_id AS coverAssetId, "
                + "c.code AS categoryCode, t.published_at AS publishedAt "
                + "FROM design_template t "
                + "LEFT JOIN template_category c "
                + "ON c.id = t.category_id AND c.status = 'PUBLISHED' "
                + where()
                + " ORDER BY t.featured_rank IS NULL ASC, t.featured_rank ASC, "
                + "t.published_at DESC, t.id DESC "
                + "LIMIT #{limit} OFFSET #{offset}"
                + "</script>";
        }

        public String count(Map<String, Object> parameters) {
            return "<script>"
                + "SELECT COUNT(*) FROM design_template t "
                + where()
                + "</script>";
        }

        private String where() {
            return "WHERE t.status = 'PUBLISHED' "
                + "<if test='keyword != null'>"
                + "AND LOWER(t.name) LIKE CONCAT('%', LOWER(#{keyword}), '%') "
                + "</if>"
                + "<if test='categoryCode != null'>"
                + "AND EXISTS (SELECT 1 FROM template_category c2 "
                + "WHERE c2.id = t.category_id AND c2.status = 'PUBLISHED' "
                + "AND c2.code = #{categoryCode}) "
                + "</if>"
                + "<if test='tagCode != null'>"
                + "AND EXISTS (SELECT 1 FROM template_tag_relation r "
                + "JOIN template_tag g ON g.id = r.tag_id AND g.status = 'PUBLISHED' "
                + "WHERE r.template_id = t.id AND g.code = #{tagCode}) "
                + "</if>";
        }
    }

    class TemplateRow {
        private long id;
        private String name;
        private int width;
        private int height;
        private Long coverAssetId;
        private String categoryCode;
        private Instant publishedAt;
        private String schemaJson;

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
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

        public Long getCoverAssetId() {
            return coverAssetId;
        }

        public void setCoverAssetId(Long coverAssetId) {
            this.coverAssetId = coverAssetId;
        }

        public String getCategoryCode() {
            return categoryCode;
        }

        public void setCategoryCode(String categoryCode) {
            this.categoryCode = categoryCode;
        }

        public Instant getPublishedAt() {
            return publishedAt;
        }

        public void setPublishedAt(Instant publishedAt) {
            this.publishedAt = publishedAt;
        }

        public String getSchemaJson() {
            return schemaJson;
        }

        public void setSchemaJson(String schemaJson) {
            this.schemaJson = schemaJson;
        }
    }

    class AdminTemplateRow {
        private long id;
        private Long categoryId;
        private String name;
        private int width;
        private int height;
        private Long coverAssetId;
        private String categoryCode;
        private Integer featuredRank;
        private String status;
        private Instant publishedAt;
        private Instant updatedAt;
        private String schemaJson;

        public long getId() { return id; }
        public void setId(long id) { this.id = id; }
        public Long getCategoryId() { return categoryId; }
        public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getWidth() { return width; }
        public void setWidth(int width) { this.width = width; }
        public int getHeight() { return height; }
        public void setHeight(int height) { this.height = height; }
        public Long getCoverAssetId() { return coverAssetId; }
        public void setCoverAssetId(Long coverAssetId) { this.coverAssetId = coverAssetId; }
        public String getCategoryCode() { return categoryCode; }
        public void setCategoryCode(String categoryCode) { this.categoryCode = categoryCode; }
        public Integer getFeaturedRank() { return featuredRank; }
        public void setFeaturedRank(Integer featuredRank) { this.featuredRank = featuredRank; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Instant getPublishedAt() { return publishedAt; }
        public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
        public Instant getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
        public String getSchemaJson() { return schemaJson; }
        public void setSchemaJson(String schemaJson) { this.schemaJson = schemaJson; }
    }
}
