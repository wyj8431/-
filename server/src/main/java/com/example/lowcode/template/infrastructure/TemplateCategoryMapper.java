package com.example.lowcode.template.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface TemplateCategoryMapper {
    @Select("""
        <script>
        SELECT c.id, c.code, c.name, p.code AS parentCode, c.sort_order AS sortOrder, c.status
        FROM template_category c
        LEFT JOIN template_category p ON p.id = c.parent_id
        <where>
            <if test="status != null and status != ''">c.status = #{status}</if>
        </where>
        ORDER BY c.sort_order ASC, c.id ASC
        </script>
        """)
    List<AdminCategoryRow> findAll(@Param("status") String status);

    @Update("""
        UPDATE template_category
        SET status = #{status}
        WHERE code = #{code}
        """)
    int updateStatus(@Param("code") String code, @Param("status") String status);

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

    class AdminCategoryRow {
        private long id;
        private String code;
        private String name;
        private String parentCode;
        private int sortOrder;
        private String status;

        public long getId() { return id; }
        public void setId(long id) { this.id = id; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getParentCode() { return parentCode; }
        public void setParentCode(String parentCode) { this.parentCode = parentCode; }
        public int getSortOrder() { return sortOrder; }
        public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
