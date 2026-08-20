package com.example.lowcode.auth.infrastructure;

import com.example.lowcode.auth.domain.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper {
    @Select("SELECT id, phone, status, security_version AS securityVersion FROM sys_user WHERE phone = #{phone} LIMIT 1")
    User findByPhone(@Param("phone") String phone);

    @Select("SELECT id, phone, status, security_version AS securityVersion FROM sys_user WHERE phone = #{phone} LIMIT 1 FOR UPDATE")
    User findByPhoneForUpdate(@Param("phone") String phone);

    @Select("SELECT id, phone, status, security_version AS securityVersion FROM sys_user WHERE wechat_open_id = #{openId} LIMIT 1")
    User findByWechatOpenId(@Param("openId") String openId);

    @Select("SELECT wechat_open_id FROM sys_user WHERE id = #{userId} LIMIT 1")
    String findWechatOpenIdByUserId(@Param("userId") long userId);

    @Update("""
        UPDATE sys_user
        SET wechat_open_id = #{openId}
        WHERE id = #{userId}
          AND wechat_open_id IS NULL
        """)
    int bindWechatOpenId(@Param("userId") long userId, @Param("openId") String openId);

    @Select("""
        SELECT u.id, u.phone, u.status, u.security_version AS securityVersion,
               tm.tenant_id AS tenantId, t.status AS tenantStatus, tm.role AS tenantRole
        FROM sys_user u
        INNER JOIN sys_tenant_member tm ON tm.user_id = u.id
        INNER JOIN sys_tenant t ON t.id = tm.tenant_id
        WHERE u.id = #{userId} AND tm.tenant_id = #{tenantId}
        LIMIT 1
        """)
    UserIdentityRow findByUserAndTenant(
        @Param("userId") long userId,
        @Param("tenantId") long tenantId
    );

    @Insert("""
        INSERT INTO sys_user (phone, status)
        VALUES (#{phone}, 'ACTIVE')
        ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id)
        """)
    int insertOrAcquire(@Param("phone") String phone);

    @Update("UPDATE sys_user SET security_version = security_version + 1 WHERE id = #{userId}")
    int incrementSecurityVersion(@Param("userId") long userId);

    class UserIdentityRow {
        private Long id;
        private String phone;
        private String status;
        private int securityVersion;
        private Long tenantId;
        private String tenantStatus;
        private String tenantRole;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public int getSecurityVersion() { return securityVersion; }
        public void setSecurityVersion(int securityVersion) { this.securityVersion = securityVersion; }
        public Long getTenantId() { return tenantId; }
        public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
        public String getTenantStatus() { return tenantStatus; }
        public void setTenantStatus(String tenantStatus) { this.tenantStatus = tenantStatus; }
        public String getTenantRole() { return tenantRole; }
        public void setTenantRole(String tenantRole) { this.tenantRole = tenantRole; }
    }
}
