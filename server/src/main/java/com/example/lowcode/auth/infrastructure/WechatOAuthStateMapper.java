package com.example.lowcode.auth.infrastructure;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.sql.Timestamp;

@Mapper
public interface WechatOAuthStateMapper {
    @Insert("""
        INSERT INTO auth_wechat_oauth_state (
            state_hash, purpose, initiator_user_id, initiator_tenant_id,
            return_path, expires_at
        ) VALUES (
            #{stateHash}, #{purpose}, #{initiatorUserId}, #{initiatorTenantId},
            #{returnPath}, #{expiresAt}
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(StateRow row);

    @Select("""
        SELECT id,
               state_hash AS stateHash,
               purpose,
               initiator_user_id AS initiatorUserId,
               initiator_tenant_id AS initiatorTenantId,
               return_path AS returnPath,
               expires_at AS expiresAt,
               consumed_at AS consumedAt
        FROM auth_wechat_oauth_state
        WHERE state_hash = #{stateHash}
        FOR UPDATE
        """)
    StateRow findByHashForUpdate(@Param("stateHash") String stateHash);

    @Update("""
        UPDATE auth_wechat_oauth_state
        SET consumed_at = #{consumedAt}
        WHERE id = #{id}
          AND consumed_at IS NULL
        """)
    int markConsumed(@Param("id") long id, @Param("consumedAt") Timestamp consumedAt);

    class StateRow {
        private Long id;
        private String stateHash;
        private String purpose;
        private Long initiatorUserId;
        private Long initiatorTenantId;
        private String returnPath;
        private Timestamp expiresAt;
        private Timestamp consumedAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getStateHash() { return stateHash; }
        public void setStateHash(String stateHash) { this.stateHash = stateHash; }
        public String getPurpose() { return purpose; }
        public void setPurpose(String purpose) { this.purpose = purpose; }
        public Long getInitiatorUserId() { return initiatorUserId; }
        public void setInitiatorUserId(Long initiatorUserId) { this.initiatorUserId = initiatorUserId; }
        public Long getInitiatorTenantId() { return initiatorTenantId; }
        public void setInitiatorTenantId(Long initiatorTenantId) { this.initiatorTenantId = initiatorTenantId; }
        public String getReturnPath() { return returnPath; }
        public void setReturnPath(String returnPath) { this.returnPath = returnPath; }
        public Timestamp getExpiresAt() { return expiresAt; }
        public void setExpiresAt(Timestamp expiresAt) { this.expiresAt = expiresAt; }
        public Timestamp getConsumedAt() { return consumedAt; }
        public void setConsumedAt(Timestamp consumedAt) { this.consumedAt = consumedAt; }
    }
}
