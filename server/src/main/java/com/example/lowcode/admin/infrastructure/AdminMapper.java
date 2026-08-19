package com.example.lowcode.admin.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.sql.Timestamp;
import java.util.List;

@Mapper
public interface AdminMapper {
    @Select("""
        SELECT
            COUNT(*) AS memberCount,
            COUNT(CASE WHEN u.status = 'ACTIVE' THEN 1 END) AS activeUserCount,
            (
                SELECT COUNT(*)
                FROM sys_audit_log a
                WHERE a.tenant_id = #{tenantId}
            ) AS auditCount
        FROM sys_tenant_member tm
        INNER JOIN sys_user u ON u.id = tm.user_id
        WHERE tm.tenant_id = #{tenantId}
        """)
    SummaryRow findSummary(@Param("tenantId") long tenantId);

    @Select("""
        <script>
        SELECT
            tm.id AS memberId,
            u.id AS userId,
            u.phone AS phone,
            u.status AS userStatus,
            tm.role AS tenantRole,
            tm.created_at AS joinedAt
        FROM sys_tenant_member tm
        INNER JOIN sys_user u ON u.id = tm.user_id
        WHERE tm.tenant_id = #{tenantId}
        <if test="role != null and role != ''">
            AND tm.role = #{role}
        </if>
        <if test="status != null and status != ''">
            AND u.status = #{status}
        </if>
        ORDER BY tm.created_at DESC, tm.id DESC
        LIMIT #{pageSize} OFFSET #{offset}
        </script>
        """)
    List<MemberRow> findMembers(
        @Param("tenantId") long tenantId,
        @Param("role") String role,
        @Param("status") String status,
        @Param("pageSize") int pageSize,
        @Param("offset") long offset
    );

    @Select("""
        <script>
        SELECT COUNT(*)
        FROM sys_tenant_member tm
        INNER JOIN sys_user u ON u.id = tm.user_id
        WHERE tm.tenant_id = #{tenantId}
        <if test="role != null and role != ''">
            AND tm.role = #{role}
        </if>
        <if test="status != null and status != ''">
            AND u.status = #{status}
        </if>
        </script>
        """)
    long countMembers(
        @Param("tenantId") long tenantId,
        @Param("role") String role,
        @Param("status") String status
    );

    class MemberRow {
        private long memberId;
        private long userId;
        private String phone;
        private String userStatus;
        private String tenantRole;
        private Timestamp joinedAt;

        public long getMemberId() { return memberId; }
        public void setMemberId(long memberId) { this.memberId = memberId; }
        public long getUserId() { return userId; }
        public void setUserId(long userId) { this.userId = userId; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getUserStatus() { return userStatus; }
        public void setUserStatus(String userStatus) { this.userStatus = userStatus; }
        public String getTenantRole() { return tenantRole; }
        public void setTenantRole(String tenantRole) { this.tenantRole = tenantRole; }
        public Timestamp getJoinedAt() { return joinedAt; }
        public void setJoinedAt(Timestamp joinedAt) { this.joinedAt = joinedAt; }
    }

    class SummaryRow {
        private long memberCount;
        private long activeUserCount;
        private long auditCount;

        public long getMemberCount() { return memberCount; }
        public void setMemberCount(long memberCount) { this.memberCount = memberCount; }
        public long getActiveUserCount() { return activeUserCount; }
        public void setActiveUserCount(long activeUserCount) { this.activeUserCount = activeUserCount; }
        public long getAuditCount() { return auditCount; }
        public void setAuditCount(long auditCount) { this.auditCount = auditCount; }
    }
}
