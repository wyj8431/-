package com.example.lowcode.template.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.sql.Timestamp;

@Mapper
public interface TemplateCoverAssetMapper {
    @Select("""
        SELECT a.id, a.object_key AS objectKey, a.mime_type AS mimeType
        FROM template_cover_asset a
        WHERE a.id = #{coverAssetId}
          AND a.status = 'PUBLISHED'
          AND a.object_key IS NOT NULL
          AND (
              EXISTS (
                  SELECT 1 FROM design_template t
                  WHERE t.cover_asset_id = a.id AND t.status = 'PUBLISHED'
              )
              OR EXISTS (
                  SELECT 1 FROM home_topic h
                  WHERE h.cover_asset_id = a.id
                    AND h.status = 'PUBLISHED'
                    AND (h.starts_at IS NULL OR h.starts_at <= #{now})
                    AND (h.ends_at IS NULL OR h.ends_at > #{now})
              )
          )
        """)
    CoverRow findPublicReferencedCover(
        @Param("coverAssetId") long coverAssetId,
        @Param("now") Timestamp now
    );

    class CoverRow {
        private long id;
        private String objectKey;
        private String mimeType;

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public String getObjectKey() {
            return objectKey;
        }

        public void setObjectKey(String objectKey) {
            this.objectKey = objectKey;
        }

        public String getMimeType() {
            return mimeType;
        }

        public void setMimeType(String mimeType) {
            this.mimeType = mimeType;
        }
    }
}
