package com.example.lowcode.template.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;

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
}
