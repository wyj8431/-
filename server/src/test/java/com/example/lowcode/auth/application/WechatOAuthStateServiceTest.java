package com.example.lowcode.auth.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WechatOAuthStateServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-19T12:00:00Z");

    private InMemoryStateRepository repository;
    private WechatOAuthStateService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryStateRepository();
        service = new WechatOAuthStateService(
            repository,
            Clock.fixed(NOW, ZoneOffset.UTC),
            Duration.ofMinutes(5)
        );
    }

    @Test
    void createsOnlyAHashAndConsumesStateOnce() {
        WechatOAuthStateService.CreatedState created = service.create(
            WechatOAuthStateService.Purpose.LOGIN,
            null,
            null,
            "/templates"
        );

        assertThat(created.rawState()).hasSizeGreaterThan(30);
        assertThat(repository.states()).singleElement().satisfies(saved -> {
            assertThat(saved.stateHash()).isEqualTo(WechatOAuthStateService.sha256(created.rawState()));
            assertThat(saved.stateHash()).doesNotContain(created.rawState());
            assertThat(saved.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
        });
        assertThat(service.consume(created.rawState())).contains(
            new WechatOAuthStateService.ConsumedState(
                WechatOAuthStateService.Purpose.LOGIN,
                null,
                null,
                "/templates"
            )
        );
        assertThat(service.consume(created.rawState())).isEmpty();
    }

    @Test
    void rejectsExpiredState() {
        WechatOAuthStateService.CreatedState created = service.create(
            WechatOAuthStateService.Purpose.BIND,
            7L,
            11L,
            "/account"
        );
        repository.expire(created.rawState(), NOW.minusSeconds(1));

        assertThat(service.consume(created.rawState())).isEmpty();
    }

    @Test
    void rejectsMalformedRawStateAndInvalidPurposeIdentity() {
        assertThat(service.consume(" ")).isEmpty();
        assertThat(service.consume("x".repeat(257))).isEmpty();
        assertThatThrownBy(() -> service.create(
            WechatOAuthStateService.Purpose.BIND,
            7L,
            null,
            "/account"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private static final class InMemoryStateRepository implements WechatOAuthStateRepository {
        private final Map<String, StoredState> states = new LinkedHashMap<>();
        private long nextId = 1;

        @Override
        public void insert(NewState state) {
            states.put(state.stateHash(), new StoredState(
                nextId++,
                state.stateHash(),
                state.purpose(),
                state.initiatorUserId(),
                state.initiatorTenantId(),
                state.returnPath(),
                state.expiresAt(),
                null
            ));
        }

        @Override
        public Optional<StoredState> findByHashForUpdate(String stateHash) {
            return Optional.ofNullable(states.get(stateHash));
        }

        @Override
        public boolean markConsumed(long id, Instant consumedAt) {
            for (Map.Entry<String, StoredState> entry : states.entrySet()) {
                StoredState state = entry.getValue();
                if (state.id() == id && state.consumedAt() == null) {
                    entry.setValue(new StoredState(
                        state.id(), state.stateHash(), state.purpose(), state.initiatorUserId(),
                        state.initiatorTenantId(), state.returnPath(), state.expiresAt(), consumedAt
                    ));
                    return true;
                }
            }
            return false;
        }

        void expire(String rawState, Instant expiresAt) {
            String hash = WechatOAuthStateService.sha256(rawState);
            StoredState state = states.get(hash);
            states.put(hash, new StoredState(
                state.id(), state.stateHash(), state.purpose(), state.initiatorUserId(),
                state.initiatorTenantId(), state.returnPath(), expiresAt, state.consumedAt()
            ));
        }

        Iterable<StoredState> states() {
            return states.values();
        }
    }
}
