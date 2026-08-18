package com.example.lowcode.template.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TemplateCategoryMapper {
    @Select("""
        SELECT c.code, c.name, p.code AS parentCode
        FROM template_category c
        LEFT JOIN template_category p
          ON p.id = c.parent_id AND p.status = 'PUBLISHED'
        WHERE c.status = 'PUBLISHED'
        ORDER BY c.sort_order ASC, c.id ASC
        """)
    List<CategoryRow> findPublished();

    @Select("""
        SELECT COUNT(*)
        FROM template_category
        WHERE code = #{code} AND status = 'PUBLISHED'
        """)
    int countPublishedByCode(@Param("code") String code);

    class CategoryRow {
        private String code;
        private String name;
        private String parentCode;

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getParentCode() {
            return parentCode;
        }

        public void setParentCode(String parentCode) {
            this.parentCode = parentCode;
        }
    }
}
