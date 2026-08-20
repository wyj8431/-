package com.example.lowcode.auth.application;

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
import java.util.Optional;

@Service
public class WechatOAuthStateService {
    private static final int STATE_BYTES = 32;
    private static final int MAX_RAW_STATE_LENGTH = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final WechatOAuthStateRepository repository;
    private final Clock clock;
    private final Duration ttl;

    @Autowired
    public WechatOAuthStateService(
        WechatOAuthStateRepository repository,
        @Value("${app.wechat.state-ttl:PT5M}") Duration ttl
    ) {
        this(repository, Clock.systemUTC(), ttl);
    }

    WechatOAuthStateService(WechatOAuthStateRepository repository, Clock clock, Duration ttl) {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("WeChat OAuth state TTL must be positive");
        }
        this.repository = repository;
        this.clock = clock;
        this.ttl = ttl;
    }

    @Transactional
    public CreatedState create(Purpose purpose, Long initiatorUserId, Long initiatorTenantId, String returnPath) {
        validatePurposeIdentity(purpose, initiatorUserId, initiatorTenantId);
        if (returnPath == null || returnPath.isBlank() || returnPath.length() > 512) {
            throw new IllegalArgumentException("WeChat OAuth return path is invalid");
        }
        String rawState = createRawState();
        Instant expiresAt = clock.instant().plus(ttl);
        repository.insert(new WechatOAuthStateRepository.NewState(
            sha256(rawState),
            purpose.name(),
            initiatorUserId,
            initiatorTenantId,
            returnPath,
            expiresAt
        ));
        return new CreatedState(rawState, expiresAt);
    }

    @Transactional
    public Optional<ConsumedState> consume(String rawState) {
        if (rawState == null || rawState.isBlank() || rawState.length() > MAX_RAW_STATE_LENGTH) {
            return Optional.empty();
        }
        WechatOAuthStateRepository.StoredState stored = repository.findByHashForUpdate(sha256(rawState))
            .orElse(null);
        Instant now = clock.instant();
        if (stored == null || stored.consumedAt() != null || !stored.expiresAt().isAfter(now)) {
            return Optional.empty();
        }
        Purpose purpose;
        try {
            purpose = Purpose.valueOf(stored.purpose());
            validatePurposeIdentity(purpose, stored.initiatorUserId(), stored.initiatorTenantId());
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
        if (!repository.markConsumed(stored.id(), now)) {
            return Optional.empty();
        }
        return Optional.of(new ConsumedState(
            purpose,
            stored.initiatorUserId(),
            stored.initiatorTenantId(),
            stored.returnPath()
        ));
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte byteValue : digest) {
                hex.append(String.format("%02x", byteValue));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String createRawState() {
        byte[] bytes = new byte[STATE_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void validatePurposeIdentity(Purpose purpose, Long initiatorUserId, Long initiatorTenantId) {
        if (purpose == null) {
            throw new IllegalArgumentException("WeChat OAuth purpose is required");
        }
        if (purpose == Purpose.LOGIN && initiatorUserId == null && initiatorTenantId == null) {
            return;
        }
        if (purpose == Purpose.BIND
            && initiatorUserId != null && initiatorUserId > 0
            && initiatorTenantId != null && initiatorTenantId > 0) {
            return;
        }
        throw new IllegalArgumentException("WeChat OAuth state identity is invalid");
    }

    public enum Purpose {
        LOGIN,
        BIND
    }

    public record CreatedState(String rawState, Instant expiresAt) {
    }

    public record ConsumedState(
        Purpose purpose,
        Long initiatorUserId,
        Long initiatorTenantId,
        String returnPath
    ) {
    }
}
