package com.example.lowcode.auth.infrastructure;

import com.example.lowcode.auth.application.NewRefreshToken;
import com.example.lowcode.auth.application.RefreshTokenRepository;
import com.example.lowcode.auth.application.SessionIdentity;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
public class MyBatisRefreshTokenRepository implements RefreshTokenRepository {
    private final RefreshTokenMapper mapper;

    public MyBatisRefreshTokenRepository(RefreshTokenMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<RefreshToken> findByHashForUpdate(String tokenHash) {
        return Optional.ofNullable(mapper.findByHashForUpdate(tokenHash)).map(this::toRefreshToken);
    }

    @Override
    public Optional<SessionIdentity> findActiveIdentity(long userId, long tenantId) {
        return Optional.ofNullable(mapper.findIdentityForUpdate(userId, tenantId))
            .map(row -> new SessionIdentity(
                row.getUserId(),
                row.getTenantId(),
                row.getUserStatus(),
                row.getTenantStatus(),
                row.getTenantRole(),
                row.getSecurityVersion()
            ));
    }

    @Override
    public void insert(NewRefreshToken token) {
        RefreshTokenMapper.NewRefreshTokenRow row = new RefreshTokenMapper.NewRefreshTokenRow();
        row.setTokenHash(token.tokenHash());
        row.setFamilyId(token.familyId());
        row.setUserId(token.userId());
        row.setTenantId(token.tenantId());
        row.setSecurityVersion(token.securityVersion());
        row.setExpiresAt(Timestamp.from(token.expiresAt()));
        row.setUserAgent(token.userAgent());
        row.setIpAddress(token.ipAddress());
        if (mapper.insert(row) != 1 || row.getId() == null) {
            throw new IllegalStateException("Could not create refresh token");
        }
    }

    @Override
    public void markReplaced(String oldHash, String replacementHash, Instant usedAt) {
        if (mapper.markReplaced(oldHash, replacementHash, Timestamp.from(usedAt)) != 1) {
            throw new IllegalStateException("Refresh token was already consumed");
        }
    }

    @Override
    public void revokeByHash(String tokenHash, Instant revokedAt, String reason) {
        mapper.revokeByHash(tokenHash, Timestamp.from(revokedAt), reason);
    }

    @Override
    public void revokeFamily(String familyId, Instant revokedAt, String reason) {
        mapper.revokeFamily(familyId, Timestamp.from(revokedAt), reason);
    }

    @Override
    public void revokeUser(long userId, Instant revokedAt, String reason) {
        mapper.revokeUser(userId, Timestamp.from(revokedAt), reason);
    }

    private RefreshToken toRefreshToken(RefreshTokenMapper.RefreshTokenRow row) {
        return new RefreshToken(
            row.getId(),
            row.getTokenHash(),
            row.getFamilyId(),
            row.getUserId(),
            row.getTenantId(),
            row.getSecurityVersion(),
            row.getReplacedByHash(),
            toInstant(row.getExpiresAt()),
            toInstant(row.getLastUsedAt()),
            toInstant(row.getRevokedAt()),
            row.getRevokeReason(),
            row.getUserAgent(),
            row.getIpAddress()
        );
    }

    private Instant toInstant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
