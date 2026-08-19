package com.example.lowcode.audit.infrastructure;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.sql.Timestamp;
import java.util.List;

@Mapper
public interface AuditLogMapper {
    @Insert("""
        INSERT INTO sys_audit_log (
            actor_user_id, tenant_id, action, resource_type, resource_id,
            outcome, request_id, metadata_json
        ) VALUES (
            #{actorUserId}, #{tenantId}, #{action}, #{resourceType}, #{resourceId},
            #{outcome}, #{requestId}, #{metadataJson}
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(AuditLogRow row);

    @Select("""
        <script>
        SELECT
            a.id AS id,
            a.actor_user_id AS actorUserId,
            u.phone AS actorPhone,
            a.action AS action,
            a.resource_type AS resourceType,
            a.resource_id AS resourceId,
            a.outcome AS outcome,
            a.request_id AS requestId,
            a.created_at AS createdAt
        FROM sys_audit_log a
        LEFT JOIN sys_user u ON u.id = a.actor_user_id
        WHERE a.tenant_id = #{tenantId}
        <if test="action != null and action != ''">
            AND a.action = #{action}
        </if>
        <if test="outcome != null and outcome != ''">
            AND a.outcome = #{outcome}
        </if>
        <if test="from != null">
            AND a.created_at &gt;= #{from}
        </if>
        <if test="to != null">
            AND a.created_at &lt; #{to}
        </if>
        ORDER BY a.created_at DESC, a.id DESC
        LIMIT #{pageSize} OFFSET #{offset}
        </script>
        """)
    List<AuditLogViewRow> findPage(
        @Param("tenantId") long tenantId,
        @Param("action") String action,
        @Param("outcome") String outcome,
        @Param("from") Timestamp from,
        @Param("to") Timestamp to,
        @Param("pageSize") int pageSize,
        @Param("offset") long offset
    );

    @Select("""
        <script>
        SELECT COUNT(*)
        FROM sys_audit_log a
        WHERE a.tenant_id = #{tenantId}
        <if test="action != null and action != ''">
            AND a.action = #{action}
        </if>
        <if test="outcome != null and outcome != ''">
            AND a.outcome = #{outcome}
        </if>
        <if test="from != null">
            AND a.created_at &gt;= #{from}
        </if>
        <if test="to != null">
            AND a.created_at &lt; #{to}
        </if>
        </script>
        """)
    long countPage(
        @Param("tenantId") long tenantId,
        @Param("action") String action,
        @Param("outcome") String outcome,
        @Param("from") Timestamp from,
        @Param("to") Timestamp to
    );

    class AuditLogRow {
        private Long id;
        private Long actorUserId;
        private Long tenantId;
        private String action;
        private String resourceType;
        private String resourceId;
        private String outcome;
        private String requestId;
        private String metadataJson;

        public Long id() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long actorUserId() { return actorUserId; }
        public void setActorUserId(Long actorUserId) { this.actorUserId = actorUserId; }
        public Long tenantId() { return tenantId; }
        public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
        public String action() { return action; }
        public void setAction(String action) { this.action = action; }
        public String resourceType() { return resourceType; }
        public void setResourceType(String resourceType) { this.resourceType = resourceType; }
        public String resourceId() { return resourceId; }
        public void setResourceId(String resourceId) { this.resourceId = resourceId; }
        public String outcome() { return outcome; }
        public void setOutcome(String outcome) { this.outcome = outcome; }
        public String requestId() { return requestId; }
        public void setRequestId(String requestId) { this.requestId = requestId; }
        public String metadataJson() { return metadataJson; }
        public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    }

    class AuditLogViewRow {
        private long id;
        private Long actorUserId;
        private String actorPhone;
        private String action;
        private String resourceType;
        private String resourceId;
        private String outcome;
        private String requestId;
        private Timestamp createdAt;

        public long getId() { return id; }
        public void setId(long id) { this.id = id; }
        public Long getActorUserId() { return actorUserId; }
        public void setActorUserId(Long actorUserId) { this.actorUserId = actorUserId; }
        public String getActorPhone() { return actorPhone; }
        public void setActorPhone(String actorPhone) { this.actorPhone = actorPhone; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public String getResourceType() { return resourceType; }
        public void setResourceType(String resourceType) { this.resourceType = resourceType; }
        public String getResourceId() { return resourceId; }
        public void setResourceId(String resourceId) { this.resourceId = resourceId; }
        public String getOutcome() { return outcome; }
        public void setOutcome(String outcome) { this.outcome = outcome; }
        public String getRequestId() { return requestId; }
        public void setRequestId(String requestId) { this.requestId = requestId; }
        public Timestamp getCreatedAt() { return createdAt; }
        public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    }
}
