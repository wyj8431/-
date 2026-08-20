package com.example.lowcode.auth.application;

import com.example.lowcode.audit.application.AuditLogService;
import com.example.lowcode.auth.security.CurrentUser;
import com.example.lowcode.auth.security.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WechatAuthServiceTest {
    private static final AuthRepository.UserIdentity ACTIVE_IDENTITY = new AuthRepository.UserIdentity(
        7L, 11L, "13800000000", "ACTIVE", "ACTIVE", "USER", 0
    );

    private FakeAuthRepository authRepository;
    private FakeWechatProvider provider;
    private RefreshTokenService refreshTokenService;
    private AuditLogService auditLogService;
    private WechatAuthService service;

    @BeforeEach
    void setUp() {
        authRepository = new FakeAuthRepository();
        authRepository.put(ACTIVE_IDENTITY);
        provider = new FakeWechatProvider();
        refreshTokenService = mock(RefreshTokenService.class);
        auditLogService = mock(AuditLogService.class);
        AuthService authService = new AuthService(
            authRepository,
            (phone, code) -> true,
            new JwtTokenService("test-signing-secret-must-have-at-least-thirty-two-bytes", Duration.ofMinutes(15)),
            refreshTokenService,
            auditLogService
        );
        WechatOAuthStateService stateService = new WechatOAuthStateService(
            new InMemoryStateRepository(),
            Clock.fixed(Instant.parse("2026-08-19T12:00:00Z"), ZoneOffset.UTC),
            Duration.ofMinutes(5)
        );
        service = new WechatAuthService(stateService, provider, authRepository, authService, auditLogService);
    }

    @Test
    void boundWechatLoginIssuesExistingSessionAndRedactsAuditMetadata() {
        authRepository.bindExisting("openid-bound", ACTIVE_IDENTITY.userId());
        provider.openIdForCode("bound", "openid-bound");
        when(refreshTokenService.issue(7L, 11L, "USER", 0, "browser", "127.0.0.1"))
            .thenReturn(new RefreshTokenService.IssuedSession("refresh-token", "access-token", 900));
        WechatAuthService.PreparedAuthorization authorization = service.beginLogin("/templates");

        WechatAuthService.CallbackResult result = service.complete(
            "bound", authorization.state(), "browser", "127.0.0.1"
        );

        assertThat(result.kind()).isEqualTo(WechatAuthService.CallbackResultKind.SUCCESS);
        assertThat(result.returnPath()).isEqualTo("/templates");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        verify(refreshTokenService).issue(7L, 11L, "USER", 0, "browser", "127.0.0.1");
        ArgumentCaptor<AuditLogService.AuditEvent> event = ArgumentCaptor.forClass(AuditLogService.AuditEvent.class);
        verify(auditLogService).record(event.capture());
        assertThat(event.getValue().action()).isEqualTo("WECHAT_LOGIN");
        assertThat(event.getValue().metadata().get("purpose")).isEqualTo("LOGIN");
        assertThat(event.getValue().metadata().get("result")).isEqualTo("SUCCESS");
        assertThat(event.getValue().metadata().toString()).doesNotContain("openid-bound");
    }

    @Test
    void unboundWechatLoginNeverCreatesAnAccountOrSession() {
        provider.openIdForCode("unbound", "openid-unbound");
        WechatAuthService.PreparedAuthorization authorization = service.beginLogin("/");

        WechatAuthService.CallbackResult result = service.complete("unbound", authorization.state(), null, null);

        assertThat(result.kind()).isEqualTo(WechatAuthService.CallbackResultKind.UNBOUND);
        assertThat(authRepository.findByWechatOpenId("openid-unbound")).isEmpty();
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void bindingRechecksTheOriginalTenantIdentityBeforeWritingOpenId() {
        WechatAuthService.PreparedAuthorization authorization = service.beginBind(new CurrentUser(7L, 11L));
        authRepository.put(new AuthRepository.UserIdentity(
            7L, 11L, "13800000000", "ACTIVE", "DISABLED", "USER", 0
        ));
        provider.openIdForCode("bind", "openid-new");

        WechatAuthService.CallbackResult result = service.complete("bind", authorization.state(), null, null);

        assertThat(result.kind()).isEqualTo(WechatAuthService.CallbackResultKind.FAILED);
        assertThat(authRepository.findByWechatOpenId("openid-new")).isEmpty();
    }

    @Test
    void consumedStateCannotInvokeTheProviderTwice() {
        authRepository.bindExisting("openid-bound", ACTIVE_IDENTITY.userId());
        provider.openIdForCode("bound", "openid-bound");
        when(refreshTokenService.issue(7L, 11L, "USER", 0, null, null))
            .thenReturn(new RefreshTokenService.IssuedSession("refresh-token", "access-token", 900));
        WechatAuthService.PreparedAuthorization authorization = service.beginLogin("/");

        assertThat(service.complete("bound", authorization.state(), null, null).kind())
            .isEqualTo(WechatAuthService.CallbackResultKind.SUCCESS);
        assertThat(service.complete("bound", authorization.state(), null, null).kind())
            .isEqualTo(WechatAuthService.CallbackResultKind.EXPIRED);
        assertThat(provider.exchangeCount()).isEqualTo(1);
    }

    private static final class FakeWechatProvider implements WechatOAuthProvider {
        private final Map<String, String> openIds = new HashMap<>();
        private int exchanges;

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public String authorizeUrl(String state) {
            return "https://wechat.test/authorize?state=" + state;
        }

        @Override
        public OpenId exchangeCode(String code) {
            exchanges++;
            String openId = openIds.get(code);
            if (openId == null) {
                throw new ExchangeFailedException();
            }
            return new OpenId(openId);
        }

        void openIdForCode(String code, String openId) {
            openIds.put(code, openId);
        }

        int exchangeCount() {
            return exchanges;
        }
    }

    private static final class FakeAuthRepository implements AuthRepository {
        private final Map<Long, UserIdentity> identities = new HashMap<>();
        private final Map<String, Long> userIdsByOpenId = new HashMap<>();

        @Override
        public Optional<UserIdentity> findByPhone(String phone) {
            return identities.values().stream().filter(identity -> identity.phone().equals(phone)).findFirst();
        }

        @Override
        public Optional<UserIdentity> findByWechatOpenId(String openId) {
            return Optional.ofNullable(userIdsByOpenId.get(openId)).map(identities::get);
        }

        @Override
        public Optional<String> findWechatOpenIdByUserId(long userId) {
            return userIdsByOpenId.entrySet().stream()
                .filter(entry -> entry.getValue() == userId)
                .map(Map.Entry::getKey)
                .findFirst();
        }

        @Override
        public boolean bindWechatOpenId(long userId, String openId) {
            if (userIdsByOpenId.containsKey(openId) || findWechatOpenIdByUserId(userId).isPresent()) {
                return false;
            }
            userIdsByOpenId.put(openId, userId);
            return true;
        }

        @Override
        public Optional<UserIdentity> findByUserAndTenant(long userId, long tenantId) {
            return Optional.ofNullable(identities.get(userId)).filter(identity -> identity.tenantId() == tenantId);
        }

        @Override
        public UserIdentity createUserWithDefaultTenant(String phone) {
            throw new UnsupportedOperationException("WeChat login must not create an account");
        }

        void put(UserIdentity identity) {
            identities.put(identity.userId(), identity);
        }

        void bindExisting(String openId, long userId) {
            userIdsByOpenId.put(openId, userId);
        }
    }

    private static final class InMemoryStateRepository implements WechatOAuthStateRepository {
        private final Map<String, StoredState> states = new HashMap<>();
        private long nextId = 1;

        @Override
        public void insert(NewState state) {
            states.put(state.stateHash(), new StoredState(
                nextId++, state.stateHash(), state.purpose(), state.initiatorUserId(), state.initiatorTenantId(),
                state.returnPath(), state.expiresAt(), null
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
                        state.id(), state.stateHash(), state.purpose(), state.initiatorUserId(), state.initiatorTenantId(),
                        state.returnPath(), state.expiresAt(), consumedAt
                    ));
                    return true;
                }
            }
            return false;
        }
    }
}
