package com.example.lowcode.template.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface TemplateTagMapper {
    @Select({
        "<script>",
        "SELECT id, code, name, sort_order AS sortOrder, status",
        "FROM template_tag",
        "<where>",
        "<if test=\"status != null and status != ''\">status = #{status}</if>",
        "</where>",
        "ORDER BY sort_order ASC, id ASC",
        "</script>"
    })
    List<AdminTagRow> findAll(@Param("status") String status);

    @Select("""
        SELECT id, code, name, sort_order AS sortOrder, status
        FROM template_tag
        WHERE code = #{code}
        """)
    AdminTagRow findByCode(@Param("code") String code);

    @Select({
        "<script>",
        "SELECT id, code, name, sort_order AS sortOrder, status",
        "FROM template_tag",
        "WHERE code IN",
        "<foreach collection='codes' item='code' open='(' separator=',' close=')'>",
        "#{code}",
        "</foreach>",
        "ORDER BY sort_order ASC, id ASC",
        "</script>"
    })
    List<AdminTagRow> findAdminByCodes(@Param("codes") List<String> codes);

    @Select("""
        SELECT g.id, g.code, g.name, g.sort_order AS sortOrder, g.status
        FROM template_tag_relation r
        JOIN template_tag g ON g.id = r.tag_id
        WHERE r.template_id = #{templateId}
        ORDER BY g.sort_order ASC, g.id ASC
        """)
    List<AdminTagRow> findByTemplateId(@Param("templateId") long templateId);

    @Delete("DELETE FROM template_tag_relation WHERE template_id = #{templateId}")
    int deleteRelations(@Param("templateId") long templateId);

    @Insert({
        "<script>",
        "INSERT INTO template_tag_relation (template_id, tag_id) VALUES",
        "<foreach collection='tagIds' item='tagId' separator=','>",
        "(#{templateId}, #{tagId})",
        "</foreach>",
        "</script>"
    })
    int insertRelations(@Param("templateId") long templateId, @Param("tagIds") List<Long> tagIds);

    @Update("""
        UPDATE template_tag
        SET status = #{status}
        WHERE code = #{code}
        """)
    int updateStatus(@Param("code") String code, @Param("status") String status);

    @Insert("""
        INSERT INTO template_tag (code, name, sort_order, status)
        VALUES (#{code}, #{name}, #{sortOrder}, #{status})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(AdminTagRow row);

    @Update("""
        UPDATE template_tag
        SET name = #{name}, sort_order = #{sortOrder}
        WHERE code = #{code}
        """)
    int updateDetails(
        @Param("code") String code,
        @Param("name") String name,
        @Param("sortOrder") int sortOrder
    );

    @Select("SELECT COUNT(*) FROM template_tag_relation WHERE tag_id = #{tagId}")
    int countTemplateRelations(@Param("tagId") long tagId);

    @Delete("DELETE FROM template_tag WHERE id = #{tagId}")
    int deleteById(@Param("tagId") long tagId);

    @Select("""
        SELECT COUNT(*)
        FROM template_tag
        WHERE code = #{code} AND status = 'PUBLISHED'
        """)
    int countPublishedByCode(@Param("code") String code);

    @Select("""
        SELECT code, name
        FROM template_tag
        WHERE status = 'PUBLISHED'
        ORDER BY sort_order ASC, id ASC
        """)
    List<PublicTagRow> findPublished();

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

    @Select({
        "<script>",
        "SELECT r.template_id AS templateId, g.code AS tagCode",
        "FROM template_tag_relation r",
        "JOIN template_tag g ON g.id = r.tag_id",
        "WHERE r.template_id IN",
        "<foreach collection='templateIds' item='templateId' open='(' separator=',' close=')'>",
        "#{templateId}",
        "</foreach>",
        "ORDER BY r.template_id ASC, g.sort_order ASC, g.id ASC",
        "</script>"
    })
    List<TagRow> findByTemplateIds(@Param("templateIds") List<Long> templateIds);

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

    class PublicTagRow {
        private String code;
        private String name;

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
    }

    class AdminTagRow {
        private long id;
        private String code;
        private String name;
        private int sortOrder;
        private String status;

        public long getId() { return id; }
        public void setId(long id) { this.id = id; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getSortOrder() { return sortOrder; }
        public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
