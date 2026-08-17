package com.example.lowcode.auth.infrastructure;

import com.example.lowcode.auth.domain.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {
    @Select("SELECT id, phone, status FROM sys_user WHERE phone = #{phone} LIMIT 1")
    User findByPhone(@Param("phone") String phone);

    @Select("SELECT id, phone, status FROM sys_user WHERE phone = #{phone} LIMIT 1 FOR UPDATE")
    User findByPhoneForUpdate(@Param("phone") String phone);

    @Insert("""
        INSERT INTO sys_user (phone, status)
        VALUES (#{phone}, 'ACTIVE')
        ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id)
        """)
    int insertOrAcquire(@Param("phone") String phone);
}
