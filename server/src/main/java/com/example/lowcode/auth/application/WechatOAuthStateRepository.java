package com.example.lowcode.auth.application;

import java.time.Instant;
import java.util.Optional;

public interface WechatOAuthStateRepository {
    void insert(NewState state);

    Optional<StoredState> findByHashForUpdate(String stateHash);

    boolean markConsumed(long id, Instant consumedAt);

    record NewState(
        String stateHash,
        String purpose,
        Long initiatorUserId,
        Long initiatorTenantId,
        String returnPath,
        Instant expiresAt
    ) {
    }

    record StoredState(
        long id,
        String stateHash,
        String purpose,
        Long initiatorUserId,
        Long initiatorTenantId,
        String returnPath,
        Instant expiresAt,
        Instant consumedAt
    ) {
    }
}
