package com.example.lowcode.template.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DesignTemplateMapper {
    @Select("""
        SELECT id, name, width, height, cover_asset_id AS coverAssetId, schema_json AS schemaJson
        FROM design_template
        WHERE status = 'PUBLISHED'
        ORDER BY published_at DESC, id DESC
        """)
    List<TemplateRow> findPublished();

    @Select("""
        SELECT id, name, width, height, cover_asset_id AS coverAssetId, schema_json AS schemaJson
        FROM design_template
        WHERE id = #{templateId} AND status = 'PUBLISHED'
        """)
    TemplateRow findPublishedById(@Param("templateId") long templateId);

    class TemplateRow {
        private long id;
        private String name;
        private int width;
        private int height;
        private Long coverAssetId;
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

        public String getSchemaJson() {
            return schemaJson;
        }

        public void setSchemaJson(String schemaJson) {
            this.schemaJson = schemaJson;
        }
    }
}
