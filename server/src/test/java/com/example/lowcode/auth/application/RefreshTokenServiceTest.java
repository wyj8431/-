package com.example.lowcode.auth.application;

import com.example.lowcode.auth.security.JwtTokenService;
import com.example.lowcode.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefreshTokenServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-18T08:00:00Z");
    private final InMemoryRefreshTokenRepository repository = new InMemoryRefreshTokenRepository();
    private final RefreshTokenService service = new RefreshTokenService(
        repository,
        new JwtTokenService("test-signing-secret-must-have-at-least-thirty-two-bytes", Duration.ofMinutes(15)),
        Clock.fixed(NOW, ZoneOffset.UTC),
        Duration.ofDays(30)
    );

    @Test
    void issueCreatesHighEntropyRefreshTokenAndAccessToken() {
        RefreshTokenService.IssuedSession session = service.issue(
            7L, 11L, "ADMIN", 0, "browser", "127.0.0.1"
        );

        assertThat(session.refreshToken()).hasSizeGreaterThan(40);
        assertThat(session.accessToken()).isNotBlank();
        assertThat(session.expiresInSeconds()).isEqualTo(900);
        assertThat(repository.tokens()).hasSize(1);
        assertThat(repository.tokens().get(0).tokenHash()).doesNotContain(session.refreshToken());
    }

    @Test
    void rotateReplacesOldTokenAndIssuesOneNewSession() {
        RefreshTokenService.IssuedSession first = service.issue(7L, 11L, "ADMIN", 0, null, null);

        RefreshTokenService.IssuedSession second = service.rotate(first.refreshToken());

        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(repository.tokens()).hasSize(2);
        RefreshTokenRepository.RefreshToken old = repository.findByRaw(first.refreshToken());
        assertThat(old.replacedByHash()).isEqualTo(repository.hashOf(second.refreshToken()));
        assertThat(old.lastUsedAt()).isEqualTo(NOW);
    }

    @Test
    void expiredTokenIsRejectedAndMarkedRevoked() {
        RefreshTokenService.IssuedSession first = service.issue(7L, 11L, "ADMIN", 0, null, null);
        repository.expireAll();

        assertThatThrownBy(() -> service.rotate(first.refreshToken()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("过期");
        assertThat(repository.findByRaw(first.refreshToken()).revokeReason()).isEqualTo("EXPIRED");
    }

    @Test
    void replayRevokesEveryTokenInTheFamily() {
        RefreshTokenService.IssuedSession first = service.issue(7L, 11L, "ADMIN", 0, null, null);
        RefreshTokenService.IssuedSession second = service.rotate(first.refreshToken());

        assertThatThrownBy(() -> service.rotate(first.refreshToken()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("登录已失效");

        assertThat(repository.tokens())
            .allSatisfy(token -> assertThat(token.revokedAt()).isEqualTo(NOW));
        assertThatThrownBy(() -> service.rotate(second.refreshToken()))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void securityVersionMismatchRevokesFamily() {
        RefreshTokenService.IssuedSession first = service.issue(7L, 11L, "ADMIN", 0, null, null);
        repository.setSecurityVersion(1);

        assertThatThrownBy(() -> service.rotate(first.refreshToken()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("登录已失效");
        assertThat(repository.findByRaw(first.refreshToken()).revokeReason()).isEqualTo("SECURITY_VERSION");
    }

    @Test
    void revokeIsIdempotentAndInvalidatesTheWholeFamily() {
        RefreshTokenService.IssuedSession first = service.issue(7L, 11L, "ADMIN", 0, null, null);
        RefreshTokenService.IssuedSession second = service.rotate(first.refreshToken());

        service.revoke(second.refreshToken(), "LOGOUT");
        service.revoke(second.refreshToken(), "LOGOUT");

        assertThat(repository.tokens()).allSatisfy(token -> {
            assertThat(token.revokedAt()).isEqualTo(NOW);
            assertThat(token.revokeReason()).isEqualTo("LOGOUT");
        });
    }

    private static final class InMemoryRefreshTokenRepository implements RefreshTokenRepository {
        private final Map<String, RefreshToken> byHash = new HashMap<>();
        private final List<RefreshToken> inserted = new ArrayList<>();
        private final Map<String, SessionIdentity> identities = new HashMap<>();
        private int securityVersion;

        @Override
        public Optional<RefreshToken> findByHashForUpdate(String tokenHash) {
            return Optional.ofNullable(byHash.get(tokenHash));
        }

        @Override
        public Optional<SessionIdentity> findActiveIdentity(long userId, long tenantId) {
            return Optional.of(new SessionIdentity(userId, tenantId, "ACTIVE", "ACTIVE", "ADMIN", securityVersion));
        }

        @Override
        public void insert(NewRefreshToken token) {
            RefreshToken stored = new RefreshToken(
                inserted.size() + 1L,
                token.tokenHash(),
                token.familyId(),
                token.userId(),
                token.tenantId(),
                token.securityVersion(),
                null,
                token.expiresAt(),
                null,
                null,
                null,
                token.userAgent(),
                token.ipAddress()
            );
            inserted.add(stored);
            byHash.put(stored.tokenHash(), stored);
        }

        @Override
        public void markReplaced(String oldHash, String replacementHash, Instant usedAt) {
            RefreshToken old = byHash.get(oldHash);
            byHash.put(oldHash, old.withReplacement(replacementHash, usedAt));
            replaceList(byHash.get(oldHash));
        }

        @Override
        public void revokeByHash(String tokenHash, Instant revokedAt, String reason) {
            RefreshToken token = byHash.get(tokenHash);
            if (token != null) replaceList(token.withRevoked(revokedAt, reason));
        }

        @Override
        public void revokeFamily(String familyId, Instant revokedAt, String reason) {
            for (RefreshToken token : List.copyOf(inserted)) {
                if (familyId.equals(token.familyId())) replaceList(token.withRevoked(revokedAt, reason));
            }
        }

        RefreshToken findByRaw(String rawToken) {
            return byHash.get(hashOf(rawToken));
        }

        List<RefreshToken> tokens() {
            return inserted;
        }

        String hashOf(String rawToken) {
            return RefreshTokenService.hash(rawToken);
        }

        void expireAll() {
            for (RefreshToken token : List.copyOf(inserted)) {
                replaceList(token.withExpiresAt(NOW.minusSeconds(1)));
            }
        }

        void setSecurityVersion(int version) {
            securityVersion = version;
        }

        private void replaceList(RefreshToken updated) {
            byHash.put(updated.tokenHash(), updated);
            for (int index = 0; index < inserted.size(); index++) {
                if (inserted.get(index).tokenHash().equals(updated.tokenHash())) {
                    inserted.set(index, updated);
                    return;
                }
            }
        }
    }
}
