package com.example.lowcode.auth.application;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository {
    Optional<RefreshToken> findByHashForUpdate(String tokenHash);

    Optional<SessionIdentity> findActiveIdentity(long userId, long tenantId);

    void insert(NewRefreshToken token);

    void markReplaced(String oldHash, String replacementHash, Instant usedAt);

    void revokeByHash(String tokenHash, Instant revokedAt, String reason);

    void revokeFamily(String familyId, Instant revokedAt, String reason);

    default void revokeUser(long userId, Instant revokedAt, String reason) {
    }

    record RefreshToken(
        long id,
        String tokenHash,
        String familyId,
        long userId,
        long tenantId,
        int securityVersion,
        String replacedByHash,
        Instant expiresAt,
        Instant lastUsedAt,
        Instant revokedAt,
        String revokeReason,
        String userAgent,
        String ipAddress
    ) {
        public RefreshToken withReplacement(String replacementHash, Instant usedAt) {
            return new RefreshToken(
                id, tokenHash, familyId, userId, tenantId, securityVersion,
                replacementHash, expiresAt, usedAt, revokedAt, revokeReason,
                userAgent, ipAddress
            );
        }

        public RefreshToken withRevoked(Instant revokedAt, String reason) {
            return new RefreshToken(
                id, tokenHash, familyId, userId, tenantId, securityVersion,
                replacedByHash, expiresAt, lastUsedAt, revokedAt, reason,
                userAgent, ipAddress
            );
        }

        public RefreshToken withExpiresAt(Instant expiresAt) {
            return new RefreshToken(
                id, tokenHash, familyId, userId, tenantId, securityVersion,
                replacedByHash, expiresAt, lastUsedAt, revokedAt, revokeReason,
                userAgent, ipAddress
            );
        }
    }
}
