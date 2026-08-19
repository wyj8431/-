package com.example.lowcode.auth.infrastructure;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.sql.Timestamp;

@Mapper
public interface RefreshTokenMapper {
    @Select("""
        SELECT id,
               token_hash AS tokenHash,
               family_id AS familyId,
               user_id AS userId,
               tenant_id AS tenantId,
               security_version AS securityVersion,
               replaced_by_hash AS replacedByHash,
               expires_at AS expiresAt,
               last_used_at AS lastUsedAt,
               revoked_at AS revokedAt,
               revoke_reason AS revokeReason,
               user_agent AS userAgent,
               ip_address AS ipAddress
        FROM auth_refresh_token
        WHERE token_hash = #{tokenHash}
        FOR UPDATE
        """)
    RefreshTokenRow findByHashForUpdate(@Param("tokenHash") String tokenHash);

    @Select("""
        SELECT u.id AS userId,
               tm.tenant_id AS tenantId,
               u.status AS userStatus,
               t.status AS tenantStatus,
               tm.role AS tenantRole,
               u.security_version AS securityVersion
        FROM sys_user u
        INNER JOIN sys_tenant_member tm ON tm.user_id = u.id
        INNER JOIN sys_tenant t ON t.id = tm.tenant_id
        WHERE u.id = #{userId}
          AND tm.tenant_id = #{tenantId}
        FOR UPDATE
        """)
    SessionIdentityRow findIdentityForUpdate(
        @Param("userId") long userId,
        @Param("tenantId") long tenantId
    );

    @Insert("""
        INSERT INTO auth_refresh_token (
            token_hash, family_id, user_id, tenant_id, security_version,
            expires_at, user_agent, ip_address
        ) VALUES (
            #{tokenHash}, #{familyId}, #{userId}, #{tenantId}, #{securityVersion},
            #{expiresAt}, #{userAgent}, #{ipAddress}
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(NewRefreshTokenRow token);

    @Update("""
        UPDATE auth_refresh_token
        SET replaced_by_hash = #{replacementHash},
            last_used_at = #{usedAt}
        WHERE token_hash = #{oldHash}
          AND replaced_by_hash IS NULL
          AND revoked_at IS NULL
        """)
    int markReplaced(
        @Param("oldHash") String oldHash,
        @Param("replacementHash") String replacementHash,
        @Param("usedAt") Timestamp usedAt
    );

    @Update("""
        UPDATE auth_refresh_token
        SET revoked_at = #{revokedAt},
            revoke_reason = #{reason}
        WHERE token_hash = #{tokenHash}
          AND revoked_at IS NULL
        """)
    int revokeByHash(
        @Param("tokenHash") String tokenHash,
        @Param("revokedAt") Timestamp revokedAt,
        @Param("reason") String reason
    );

    @Update("""
        UPDATE auth_refresh_token
        SET revoked_at = #{revokedAt},
            revoke_reason = #{reason}
        WHERE family_id = #{familyId}
          AND revoked_at IS NULL
        """)
    int revokeFamily(
        @Param("familyId") String familyId,
        @Param("revokedAt") Timestamp revokedAt,
        @Param("reason") String reason
    );

    @Update("""
        UPDATE auth_refresh_token
        SET revoked_at = #{revokedAt},
            revoke_reason = #{reason}
        WHERE user_id = #{userId}
          AND revoked_at IS NULL
        """)
    int revokeUser(
        @Param("userId") long userId,
        @Param("revokedAt") Timestamp revokedAt,
        @Param("reason") String reason
    );

    class RefreshTokenRow {
        private Long id;
        private String tokenHash;
        private String familyId;
        private long userId;
        private long tenantId;
        private int securityVersion;
        private String replacedByHash;
        private Timestamp expiresAt;
        private Timestamp lastUsedAt;
        private Timestamp revokedAt;
        private String revokeReason;
        private String userAgent;
        private String ipAddress;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getTokenHash() { return tokenHash; }
        public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
        public String getFamilyId() { return familyId; }
        public void setFamilyId(String familyId) { this.familyId = familyId; }
        public long getUserId() { return userId; }
        public void setUserId(long userId) { this.userId = userId; }
        public long getTenantId() { return tenantId; }
        public void setTenantId(long tenantId) { this.tenantId = tenantId; }
        public int getSecurityVersion() { return securityVersion; }
        public void setSecurityVersion(int securityVersion) { this.securityVersion = securityVersion; }
        public String getReplacedByHash() { return replacedByHash; }
        public void setReplacedByHash(String replacedByHash) { this.replacedByHash = replacedByHash; }
        public Timestamp getExpiresAt() { return expiresAt; }
        public void setExpiresAt(Timestamp expiresAt) { this.expiresAt = expiresAt; }
        public Timestamp getLastUsedAt() { return lastUsedAt; }
        public void setLastUsedAt(Timestamp lastUsedAt) { this.lastUsedAt = lastUsedAt; }
        public Timestamp getRevokedAt() { return revokedAt; }
        public void setRevokedAt(Timestamp revokedAt) { this.revokedAt = revokedAt; }
        public String getRevokeReason() { return revokeReason; }
        public void setRevokeReason(String revokeReason) { this.revokeReason = revokeReason; }
        public String getUserAgent() { return userAgent; }
        public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
        public String getIpAddress() { return ipAddress; }
        public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    }

    class SessionIdentityRow {
        private long userId;
        private long tenantId;
        private String userStatus;
        private String tenantStatus;
        private String tenantRole;
        private int securityVersion;

        public long getUserId() { return userId; }
        public void setUserId(long userId) { this.userId = userId; }
        public long getTenantId() { return tenantId; }
        public void setTenantId(long tenantId) { this.tenantId = tenantId; }
        public String getUserStatus() { return userStatus; }
        public void setUserStatus(String userStatus) { this.userStatus = userStatus; }
        public String getTenantStatus() { return tenantStatus; }
        public void setTenantStatus(String tenantStatus) { this.tenantStatus = tenantStatus; }
        public String getTenantRole() { return tenantRole; }
        public void setTenantRole(String tenantRole) { this.tenantRole = tenantRole; }
        public int getSecurityVersion() { return securityVersion; }
        public void setSecurityVersion(int securityVersion) { this.securityVersion = securityVersion; }
    }

    class NewRefreshTokenRow {
        private Long id;
        private String tokenHash;
        private String familyId;
        private long userId;
        private long tenantId;
        private int securityVersion;
        private Timestamp expiresAt;
        private String userAgent;
        private String ipAddress;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getTokenHash() { return tokenHash; }
        public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
        public String getFamilyId() { return familyId; }
        public void setFamilyId(String familyId) { this.familyId = familyId; }
        public long getUserId() { return userId; }
        public void setUserId(long userId) { this.userId = userId; }
        public long getTenantId() { return tenantId; }
        public void setTenantId(long tenantId) { this.tenantId = tenantId; }
        public int getSecurityVersion() { return securityVersion; }
        public void setSecurityVersion(int securityVersion) { this.securityVersion = securityVersion; }
        public Timestamp getExpiresAt() { return expiresAt; }
        public void setExpiresAt(Timestamp expiresAt) { this.expiresAt = expiresAt; }
        public String getUserAgent() { return userAgent; }
        public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
        public String getIpAddress() { return ipAddress; }
        public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    }
}
