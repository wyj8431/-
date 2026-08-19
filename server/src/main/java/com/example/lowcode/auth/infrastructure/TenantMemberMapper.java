package com.example.lowcode.auth.infrastructure;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface TenantMemberMapper {
    @Select("""
        SELECT tenant_id
        FROM sys_tenant_member
        WHERE user_id = #{userId}
        ORDER BY CASE role WHEN 'ADMIN' THEN 0 WHEN 'OPERATOR' THEN 1 WHEN 'USER' THEN 2 ELSE 3 END, id
        LIMIT 1
        """)
    Long findPreferredTenantId(@Param("userId") long userId);

    @Select("""
        SELECT tenant_id
        FROM sys_tenant_member
        WHERE user_id = #{userId}
        ORDER BY CASE role WHEN 'ADMIN' THEN 0 WHEN 'OPERATOR' THEN 1 WHEN 'USER' THEN 2 ELSE 3 END, id
        LIMIT 1
        FOR UPDATE
        """)
    Long findPreferredTenantIdForUpdate(@Param("userId") long userId);

    @Select("""
        SELECT role
        FROM sys_tenant_member
        WHERE user_id = #{userId}
        ORDER BY CASE role WHEN 'ADMIN' THEN 0 WHEN 'OPERATOR' THEN 1 WHEN 'USER' THEN 2 ELSE 3 END, id
        LIMIT 1
        """)
    String findPreferredTenantRole(@Param("userId") long userId);

    @Select("""
        SELECT role
        FROM sys_tenant_member
        WHERE user_id = #{userId}
        ORDER BY CASE role WHEN 'ADMIN' THEN 0 WHEN 'OPERATOR' THEN 1 WHEN 'USER' THEN 2 ELSE 3 END, id
        LIMIT 1
        FOR UPDATE
        """)
    String findPreferredTenantRoleForUpdate(@Param("userId") long userId);

    @Insert("""
        INSERT INTO sys_tenant_member (tenant_id, user_id, role)
        VALUES (#{tenantId}, #{userId}, #{role})
        """)
    int insert(
        @Param("tenantId") long tenantId,
        @Param("userId") long userId,
        @Param("role") String role
    );

    @Select("""
        SELECT role
        FROM sys_tenant_member
        WHERE tenant_id = #{tenantId} AND user_id = #{userId}
        LIMIT 1
        FOR UPDATE
        """)
    String findRoleForUpdate(@Param("tenantId") long tenantId, @Param("userId") long userId);

    @Select("""
        SELECT user_id
        FROM sys_tenant_member
        WHERE tenant_id = #{tenantId} AND role = 'ADMIN'
        FOR UPDATE
        """)
    List<Long> findAdminUserIdsForUpdate(@Param("tenantId") long tenantId);

    @Update("""
        UPDATE sys_tenant_member
        SET role = #{role}
        WHERE tenant_id = #{tenantId} AND user_id = #{userId}
        """)
    int updateRole(
        @Param("tenantId") long tenantId,
        @Param("userId") long userId,
        @Param("role") String role
    );
}
