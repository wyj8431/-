package com.example.lowcode.template.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TemplateTagMapper {
    @Select("""
        SELECT COUNT(*)
        FROM template_tag
        WHERE code = #{code} AND status = 'PUBLISHED'
        """)
    int countPublishedByCode(@Param("code") String code);

    @Select({
        "<script>",
        "SELECT r.template_id AS templateId, g.code AS tagCode",
        "FROM template_tag_relation r",
        "JOIN template_tag g ON g.id = r.tag_id AND g.status = 'PUBLISHED'",
        "WHERE r.template_id IN",
        "<foreach collection='templateIds' item='templateId' open='(' separator=',' close=')'>",
        "#{templateId}",
        "</foreach>",
        "ORDER BY r.template_id ASC, g.sort_order ASC, g.id ASC",
        "</script>"
    })
    List<TagRow> findPublishedByTemplateIds(@Param("templateIds") List<Long> templateIds);

    class TagRow {
        private long templateId;
        private String tagCode;

        public long getTemplateId() {
            return templateId;
        }

        public void setTemplateId(long templateId) {
            this.templateId = templateId;
        }

        public String getTagCode() {
            return tagCode;
        }

        public void setTagCode(String tagCode) {
            this.tagCode = tagCode;
        }
    }
}
