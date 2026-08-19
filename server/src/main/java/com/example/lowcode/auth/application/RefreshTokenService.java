package com.example.lowcode.auth.application;

import com.example.lowcode.auth.security.JwtTokenService;
import com.example.lowcode.common.exception.BusinessException;
import com.example.lowcode.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
public class RefreshTokenService {
    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final JwtTokenService jwtTokenService;
    private final Clock clock;
    private final Duration refreshTokenTtl;

    @Autowired
    public RefreshTokenService(
        RefreshTokenRepository repository,
        JwtTokenService jwtTokenService,
        @Value("${app.auth.refresh-token-ttl:P30D}") Duration refreshTokenTtl
    ) {
        this(repository, jwtTokenService, Clock.systemUTC(), refreshTokenTtl);
    }

    public RefreshTokenService(
        RefreshTokenRepository repository,
        JwtTokenService jwtTokenService,
        Clock clock,
        Duration refreshTokenTtl
    ) {
        if (refreshTokenTtl.isNegative() || refreshTokenTtl.isZero()) {
            throw new IllegalArgumentException("Refresh token TTL must be positive");
        }
        this.repository = repository;
        this.jwtTokenService = jwtTokenService;
        this.clock = clock;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    @Transactional
    public IssuedSession issue(
        long userId,
        long tenantId,
        String tenantRole,
        int securityVersion,
        String userAgent,
        String ipAddress
    ) {
        return issueInFamily(
            UUID.randomUUID().toString(),
            userId,
            tenantId,
            tenantRole,
            securityVersion,
            userAgent,
            ipAddress
        );
    }

    @Transactional
    public IssuedSession rotate(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw unauthorized("登录已失效");
        }

        String tokenHash = hash(rawRefreshToken);
        RefreshTokenRepository.RefreshToken current = repository.findByHashForUpdate(tokenHash)
            .orElseThrow(() -> unauthorized("登录已失效"));
        Instant now = clock.instant();

        if (current.replacedByHash() != null) {
            repository.revokeFamily(current.familyId(), now, "REPLAY");
            throw unauthorized("登录已失效");
        }
        if (current.revokedAt() != null) {
            throw unauthorized("登录已失效");
        }
        if (!current.expiresAt().isAfter(now)) {
            repository.revokeByHash(tokenHash, now, "EXPIRED");
            throw unauthorized("刷新令牌已过期");
        }

        SessionIdentity identity = repository.findActiveIdentity(current.userId(), current.tenantId())
            .orElseThrow(() -> revokeFamilyAndReject(current, now, "SESSION_INVALID"));
        if (!"ACTIVE".equals(identity.userStatus()) || !"ACTIVE".equals(identity.tenantStatus())) {
            repository.revokeFamily(current.familyId(), now, "SESSION_INVALID");
            throw unauthorized("登录已失效");
        }
        if (identity.securityVersion() != current.securityVersion()) {
            repository.revokeFamily(current.familyId(), now, "SECURITY_VERSION");
            throw unauthorized("登录已失效");
        }
        if (identity.userId() != current.userId() || identity.tenantId() != current.tenantId()) {
            repository.revokeFamily(current.familyId(), now, "SESSION_INVALID");
            throw unauthorized("登录已失效");
        }

        String replacement = createRawToken();
        String replacementHash = hash(replacement);
        repository.markReplaced(tokenHash, replacementHash, now);
        IssuedSession next = issueInFamily(
            current.familyId(),
            identity.userId(),
            identity.tenantId(),
            identity.tenantRole(),
            identity.securityVersion(),
            current.userAgent(),
            current.ipAddress(),
            replacement
        );
        return next;
    }

    @Transactional
    public void revoke(String rawRefreshToken, String reason) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        String tokenHash = hash(rawRefreshToken);
        RefreshTokenRepository.RefreshToken token = repository.findByHashForUpdate(tokenHash).orElse(null);
        if (token == null) {
            return;
        }
        repository.revokeFamily(token.familyId(), clock.instant(), reason == null || reason.isBlank() ? "LOGOUT" : reason);
    }

    public static String hash(String rawToken) {
        if (rawToken == null) {
            throw new IllegalArgumentException("Refresh token must not be null");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private IssuedSession issueInFamily(
        String familyId,
        long userId,
        long tenantId,
        String tenantRole,
        int securityVersion,
        String userAgent,
        String ipAddress
    ) {
        return issueInFamily(
            familyId, userId, tenantId, tenantRole, securityVersion,
            userAgent, ipAddress, createRawToken()
        );
    }

    private IssuedSession issueInFamily(
        String familyId,
        long userId,
        long tenantId,
        String tenantRole,
        int securityVersion,
        String userAgent,
        String ipAddress,
        String rawToken
    ) {
        Instant expiresAt = clock.instant().plus(refreshTokenTtl);
        String tokenHash = hash(rawToken);
        repository.insert(new NewRefreshToken(
            tokenHash, familyId, userId, tenantId, securityVersion,
            expiresAt, userAgent, ipAddress
        ));
        JwtTokenService.IssuedToken accessToken = jwtTokenService.issue(userId, tenantId, tenantRole);
        return new IssuedSession(rawToken, accessToken.value(), accessToken.expiresInSeconds());
    }

    private String createRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private BusinessException revokeFamilyAndReject(
        RefreshTokenRepository.RefreshToken token,
        Instant revokedAt,
        String reason
    ) {
        repository.revokeFamily(token.familyId(), revokedAt, reason);
        return unauthorized("登录已失效");
    }

    private BusinessException unauthorized(String message) {
        return new BusinessException(ErrorCode.UNAUTHORIZED, message);
    }

    public record IssuedSession(String refreshToken, String accessToken, long expiresInSeconds) {
    }
}
