package com.example.lowcode.design.infrastructure;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DesignPermissionMapper {
    @Insert("""
        INSERT INTO design_permission (document_id, tenant_id, user_id, role)
        VALUES (#{documentId}, #{tenantId}, #{userId}, #{role})
        """)
    int insert(
        @Param("tenantId") long tenantId,
        @Param("documentId") long documentId,
        @Param("userId") long userId,
        @Param("role") String role
    );

    @Select("""
        SELECT COUNT(*)
        FROM design_permission
        WHERE tenant_id = #{tenantId}
          AND document_id = #{documentId}
          AND user_id = #{userId}
        """)
    int countViewPermission(
        @Param("tenantId") long tenantId,
        @Param("documentId") long documentId,
        @Param("userId") long userId
    );

    @Select("""
        SELECT COUNT(*)
        FROM design_permission
        WHERE tenant_id = #{tenantId}
          AND document_id = #{documentId}
          AND user_id = #{userId}
          AND role IN ('OWNER', 'EDITOR')
        """)
    int countEditPermission(
        @Param("tenantId") long tenantId,
        @Param("documentId") long documentId,
        @Param("userId") long userId
    );
}
