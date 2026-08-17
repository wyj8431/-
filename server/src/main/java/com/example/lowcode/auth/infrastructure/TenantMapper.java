package com.example.lowcode.auth.infrastructure;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TenantMapper {
    @Insert("INSERT INTO sys_tenant (name, status) VALUES (#{name}, 'ACTIVE')")
    int insert(@Param("name") String name);

    @Select("SELECT LAST_INSERT_ID()")
    long lastInsertedId();

    @Select("SELECT status FROM sys_tenant WHERE id = #{tenantId}")
    String findStatusById(@Param("tenantId") long tenantId);

    @Select("SELECT status FROM sys_tenant WHERE id = #{tenantId} FOR UPDATE")
    String findStatusByIdForUpdate(@Param("tenantId") long tenantId);
}
