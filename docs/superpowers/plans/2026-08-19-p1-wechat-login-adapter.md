# P1 微信登录适配器与账号绑定 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a default-disabled WeChat website OAuth adapter that signs in only already-bound accounts and lets a signed-in user bind one OpenID through a replay-safe callback.

**Architecture:** The `auth` module owns a one-time OAuth state lifecycle, a provider port, WeChat-specific session orchestration, and the HTTP redirect boundary. It reuses existing `AuthService` identity validation, `RefreshTokenService` issuance, `sys_user.wechat_open_id` uniqueness, and the HttpOnly refresh cookie. Vue only starts the server-authorized redirect and consumes fixed callback outcomes; it never sees provider credentials or OAuth tokens.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Security resource server, MyBatis, Flyway/MySQL, OkHttp 5, JUnit 5/Mockito/Testcontainers, Vue 3, TypeScript, Pinia, Vue Router, Vitest.

---

## Locked File Structure

| File | Responsibility |
| --- | --- |
| `server/src/main/resources/db/migration/V7__wechat_oauth_state.sql` | State table, state-purpose and binding-identity constraints, indexes and composite member FK. |
| `server/src/main/java/com/example/lowcode/auth/application/WechatOAuthProvider.java` | Provider port and OpenID-only exchange result. |
| `server/src/main/java/com/example/lowcode/auth/application/WechatOAuthStateRepository.java` | State persistence port. |
| `server/src/main/java/com/example/lowcode/auth/application/WechatOAuthStateService.java` | Random state generation, hash-only storage, TTL and one-time consumption. |
| `server/src/main/java/com/example/lowcode/auth/application/WechatAuthService.java` | Login/bind authorization orchestration, callback outcomes and audit events. |
| `server/src/main/java/com/example/lowcode/auth/infrastructure/WechatOAuthStateMapper.java` | MyBatis row locking and state mutations. |
| `server/src/main/java/com/example/lowcode/auth/infrastructure/MyBatisWechatOAuthStateRepository.java` | Port-to-mapper conversion. |
| `server/src/main/java/com/example/lowcode/auth/infrastructure/WechatOAuthProperties.java` | Server-only WeChat and public-site configuration. |
| `server/src/main/java/com/example/lowcode/auth/infrastructure/WechatOAuthConfiguration.java` | Default-disabled provider wiring and bounded OkHttp client. |
| `server/src/main/java/com/example/lowcode/auth/infrastructure/WechatOpenPlatformProvider.java` | Official QR authorization URL and code-to-OpenID exchange. |
| `server/src/main/java/com/example/lowcode/auth/infrastructure/UnavailableWechatOAuthProvider.java` | Explicit `503` behavior when the provider is disabled or incomplete. |
| `server/src/main/java/com/example/lowcode/auth/api/WechatAuthController.java` | Public authorize/callback and protected bind-authorize endpoints. |
| `server/src/main/java/com/example/lowcode/auth/api/AuthRefreshCookieWriter.java` | Shared Cookie writer used by phone and WeChat session paths. |
| `server/src/main/java/com/example/lowcode/auth/application/AuthRepository.java` | OpenID user lookup/bind port and `wechatBound` lookup. |
| `server/src/main/java/com/example/lowcode/auth/infrastructure/UserMapper.java` | OpenID selects and atomic bind statement. |
| `server/src/main/java/com/example/lowcode/auth/infrastructure/MyBatisAuthRepository.java` | Auth port implementation for WeChat lookup/binding. |
| `server/src/main/java/com/example/lowcode/auth/application/AuthService.java` | Shared identity revalidation/session issuance and `me.wechatBound`. |
| `server/src/main/java/com/example/lowcode/auth/api/AuthController.java` | Reuse `AuthRefreshCookieWriter` only. |
| `server/src/main/java/com/example/lowcode/auth/security/SecurityConfig.java` | Permit public WeChat authorize/callback paths; retain JWT requirement for binding. |
| `server/src/main/java/com/example/lowcode/common/exception/ErrorCode.java` | Stable unavailable and binding-conflict error codes. |
| `server/src/main/resources/application.yml` | Default-disabled, environment-only WeChat configuration. |
| `server/src/test/java/com/example/lowcode/auth/application/WechatOAuthStateServiceTest.java` | Pure state lifecycle tests. |
| `server/src/test/java/com/example/lowcode/auth/application/WechatAuthServiceTest.java` | Login/bind security and audit behavior tests. |
| `server/src/test/java/com/example/lowcode/auth/api/WechatAuthControllerTest.java` | Authorization, callback redirect, Cookie and secret-leak contract tests. |
| `server/src/test/java/com/example/lowcode/auth/support/TestAuthConfiguration.java` | Deterministic enabled test provider. |
| `server/src/test/java/com/example/lowcode/integration/DatabaseMigrationIT.java` | V7 table/index/FK/check assertions. |
| `poster-client/src/api/auth.ts` | Typed begin-login and begin-binding request functions. |
| `poster-client/src/api/types.ts` | `WechatAuthorizeResult` and `CurrentIdentity.wechatBound`. |
| `poster-client/src/stores/session.ts` | In-memory binding state and `loadIdentity`. |
| `poster-client/src/features/auth/LoginDialog.vue` | Emit a real WeChat-start event instead of opening a static QR demo. |
| `poster-client/src/features/home/WorkbenchShell.vue` | Request authorization and navigate to the server-provided official URL. |
| `poster-client/src/features/account/PersonalCenter.vue` | Fetch/show binding state and start binding. |
| `poster-client/src/features/auth/WechatAuthResultPage.vue` | Restore session after fixed callback outcome and render fixed failure messages. |
| `poster-client/src/router/index.ts` | Register `/auth/wechat/result` without an auth guard. |
| `poster-client/src/features/auth/__tests__/LoginDialog.spec.ts` | WeChat-start emit regression test. |
| `poster-client/src/features/auth/__tests__/WechatAuthResultPage.spec.ts` | Success restore and fixed result-page messages. |
| `poster-client/src/features/account/__tests__/PersonalCenter.spec.ts` | Binding status and start-binding behavior. |
| `poster-client/src/api/__tests__/auth.spec.ts` | Authorize request paths and credentials behavior. |
| `README.md` | State that WeChat adapter is implemented but requires server-side credentials to enable. |
| `docs/progress/2026-08-19-p1-wechat-login-adapter.md` | Delivered scope, security decisions, verification evidence and remaining P1 work. |

## Task 1: Add the V7 state persistence contract

**Files:**
- Create: `server/src/main/resources/db/migration/V7__wechat_oauth_state.sql`
- Create: `server/src/main/java/com/example/lowcode/auth/application/WechatOAuthStateRepository.java`
- Create: `server/src/main/java/com/example/lowcode/auth/infrastructure/WechatOAuthStateMapper.java`
- Create: `server/src/main/java/com/example/lowcode/auth/infrastructure/MyBatisWechatOAuthStateRepository.java`
- Modify: `server/src/test/java/com/example/lowcode/integration/DatabaseMigrationIT.java`

- [ ] **Step 1: Write the migration integration assertions before adding V7**

Add `auth_wechat_oauth_state` to `EXPECTED_TABLES`; assert its indexes and FK, then assert its check constraints through `information_schema`:

```java
@Test
void v7AddsReplaySafeWechatOAuthState() {
    assertThat(indexNames("auth_wechat_oauth_state"))
        .contains("uk_wechat_oauth_state_hash", "idx_wechat_oauth_state_expiry");
    assertThat(foreignKeyNames("auth_wechat_oauth_state"))
        .contains("fk_wechat_oauth_state_initiator_member");
    assertThat(checkConstraintNames("auth_wechat_oauth_state"))
        .contains("chk_wechat_oauth_state_purpose", "chk_wechat_oauth_state_initiator");
}
```

Add a `checkConstraintNames(String tableName)` helper that queries `information_schema.table_constraints` with `constraint_type = 'CHECK'`, matching the existing index/FK helper style.

- [ ] **Step 2: Run the focused integration test to establish the failing migration expectation**

Run: `./mvnw -Dtest=DatabaseMigrationIT test`

Expected: FAIL because `auth_wechat_oauth_state` does not exist.

- [ ] **Step 3: Add the non-destructive V7 migration**

Create the table with this concrete SQL shape:

```sql
CREATE TABLE auth_wechat_oauth_state (
    id BIGINT NOT NULL AUTO_INCREMENT,
    state_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    purpose VARCHAR(16) NOT NULL,
    initiator_user_id BIGINT NULL,
    initiator_tenant_id BIGINT NULL,
    return_path VARCHAR(512) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    consumed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_wechat_oauth_state_hash (state_hash),
    KEY idx_wechat_oauth_state_expiry (expires_at, consumed_at),
    CONSTRAINT fk_wechat_oauth_state_initiator_member
        FOREIGN KEY (initiator_tenant_id, initiator_user_id)
        REFERENCES sys_tenant_member (tenant_id, user_id),
    CONSTRAINT chk_wechat_oauth_state_purpose
        CHECK (purpose IN ('LOGIN', 'BIND')),
    CONSTRAINT chk_wechat_oauth_state_initiator
        CHECK (
            (purpose = 'LOGIN' AND initiator_user_id IS NULL AND initiator_tenant_id IS NULL)
            OR
            (purpose = 'BIND' AND initiator_user_id IS NOT NULL AND initiator_tenant_id IS NOT NULL)
        )
);
```

Do not alter `sys_user.wechat_open_id`; its existing unique key remains the binding uniqueness guarantee.

- [ ] **Step 4: Define the state repository port and MyBatis implementation**

Use records that contain only hashed state and non-sensitive callback metadata:

```java
public interface WechatOAuthStateRepository {
    void insert(NewState state);
    Optional<StoredState> findByHashForUpdate(String stateHash);
    boolean markConsumed(long id, Instant consumedAt);

    record NewState(String stateHash, String purpose, Long initiatorUserId,
                    Long initiatorTenantId, String returnPath, Instant expiresAt) {}
    record StoredState(long id, String stateHash, String purpose, Long initiatorUserId,
                       Long initiatorTenantId, String returnPath, Instant expiresAt,
                       Instant consumedAt) {}
}
```

The mapper must select by `state_hash ... FOR UPDATE` and mark consumption atomically:

```java
@Update("""
    UPDATE auth_wechat_oauth_state
    SET consumed_at = #{consumedAt}
    WHERE id = #{id} AND consumed_at IS NULL
    """)
int markConsumed(@Param("id") long id, @Param("consumedAt") Timestamp consumedAt);
```

Make the repository translate `Timestamp` to `Instant` exactly as `MyBatisRefreshTokenRepository` does, and throw `IllegalStateException` if `insert` fails.

- [ ] **Step 5: Re-run migration verification**

Run: `./mvnw -Dtest=DatabaseMigrationIT test`

Expected: PASS, including the new V7 table/constraint test.

- [ ] **Step 6: Inspect the migration diff without staging it**

Run: `git diff --check -- server/src/main/resources/db/migration/V7__wechat_oauth_state.sql server/src/test/java/com/example/lowcode/integration/DatabaseMigrationIT.java`

Expected: no whitespace errors. Do not run `git add`, `git commit`, or `git push` in this worktree.

## Task 2: Implement replay-safe OAuth state management test-first

**Files:**
- Create: `server/src/main/java/com/example/lowcode/auth/application/WechatOAuthStateService.java`
- Create: `server/src/test/java/com/example/lowcode/auth/application/WechatOAuthStateServiceTest.java`

- [ ] **Step 1: Write state lifecycle tests using an in-memory repository and fixed clock**

Cover raw-value secrecy, successful consumption, expiry and replay in the test class:

```java
@Test
void createsOnlyAHashAndConsumesStateOnce() {
    CreatedState created = service.create(LoginPurpose.LOGIN, null, null, "/templates");

    assertThat(repository.states()).singleElement().satisfies(saved -> {
        assertThat(saved.stateHash()).isEqualTo(WechatOAuthStateService.sha256(created.rawState()));
        assertThat(saved.stateHash()).doesNotContain(created.rawState());
        assertThat(saved.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
    });
    assertThat(service.consume(created.rawState())).isPresent();
    assertThat(service.consume(created.rawState())).isEmpty();
}

@Test
void rejectsExpiredAndWrongPurposeStateWithoutCallingAProvider() {
    CreatedState created = service.create(LoginPurpose.BIND, 7L, 11L, "/account");
    repository.expire(created.rawState(), NOW.minusSeconds(1));

    assertThat(service.consume(created.rawState())).isEmpty();
}
```

The in-memory repository must enforce `markConsumed` only when `consumedAt` is null, so the test models the database invariant rather than only testing a local boolean.

- [ ] **Step 2: Run the state service test before implementation**

Run: `./mvnw -Dtest=WechatOAuthStateServiceTest test`

Expected: FAIL because `WechatOAuthStateService` and its types do not exist.

- [ ] **Step 3: Implement the minimal state service**

Use a 32-byte `SecureRandom` value, URL-safe Base64 without padding, SHA-256 hex, one transaction around `findByHashForUpdate` plus `markConsumed`, and the exact types below:

```java
public enum LoginPurpose { LOGIN, BIND }

public record CreatedState(String rawState, Instant expiresAt) {}

public record ConsumedState(LoginPurpose purpose, Long initiatorUserId,
                            Long initiatorTenantId, String returnPath) {}

@Transactional
public Optional<ConsumedState> consume(String rawState) {
    if (rawState == null || rawState.isBlank() || rawState.length() > 256) return Optional.empty();
    StoredState stored = repository.findByHashForUpdate(sha256(rawState)).orElse(null);
    if (stored == null || stored.consumedAt() != null || !stored.expiresAt().isAfter(clock.instant())) {
        return Optional.empty();
    }
    if (!repository.markConsumed(stored.id(), clock.instant())) return Optional.empty();
    return Optional.of(new ConsumedState(LoginPurpose.valueOf(stored.purpose()),
        stored.initiatorUserId(), stored.initiatorTenantId(), stored.returnPath()));
}
```

Validate `LOGIN` has two null initiator fields and `BIND` has two positive fields before inserting. Reject malformed `returnPath` here only if it cannot be stored; route normalization remains in `WechatAuthService`.

- [ ] **Step 4: Run the state unit tests**

Run: `./mvnw -Dtest=WechatOAuthStateServiceTest test`

Expected: PASS.

## Task 3: Add a default-disabled provider port and server-only configuration

**Files:**
- Create: `server/src/main/java/com/example/lowcode/auth/application/WechatOAuthProvider.java`
- Create: `server/src/main/java/com/example/lowcode/auth/infrastructure/WechatOAuthProperties.java`
- Create: `server/src/main/java/com/example/lowcode/auth/infrastructure/WechatOAuthConfiguration.java`
- Create: `server/src/main/java/com/example/lowcode/auth/infrastructure/WechatOpenPlatformProvider.java`
- Create: `server/src/main/java/com/example/lowcode/auth/infrastructure/UnavailableWechatOAuthProvider.java`
- Modify: `server/src/main/resources/application.yml`
- Modify: `server/src/test/java/com/example/lowcode/auth/support/TestAuthConfiguration.java`
- Create: `server/src/test/java/com/example/lowcode/auth/infrastructure/WechatOpenPlatformProviderTest.java`

- [ ] **Step 1: Write provider URL and no-secret-leak tests**

Use OkHttp `MockWebServer` or a custom test `Call.Factory` to assert the provider constructs the official QR URL and parses only `openid`:

```java
@Test
void buildsOfficialAuthorizationUrlWithEncodedFixedRedirectAndOpaqueState() {
    String url = provider.authorizeUrl("opaque-state");

    assertThat(url).startsWith("https://open.weixin.qq.com/connect/qrconnect?");
    assertThat(url).contains("appid=test-app-id", "response_type=code", "scope=snsapi_login", "state=opaque-state");
    assertThat(url).contains("redirect_uri=https%3A%2F%2Fexample.test%2Fapi%2Fv1%2Fauth%2Fwechat%2Fcallback");
    assertThat(url).doesNotContain("test-secret");
}

@Test
void exchangesCodeForOpenIdAndMapsProviderErrorWithoutReturningPayload() {
    server.enqueue(new MockResponse().setBody("{\"errcode\":40029,\"errmsg\":\"invalid code\"}"));

    assertThatThrownBy(() -> provider.exchangeCode("one-time-code"))
        .isInstanceOf(WechatOAuthProvider.ExchangeFailedException.class);
}
```

- [ ] **Step 2: Run the provider test before adding the provider**

Run: `./mvnw -Dtest=WechatOpenPlatformProviderTest test`

Expected: FAIL because provider classes are absent.

- [ ] **Step 3: Implement the provider boundary and properties**

Keep the port limited to exactly the two operations needed by the service:

```java
public interface WechatOAuthProvider {
    boolean available();
    String authorizeUrl(String state);
    OpenId exchangeCode(String code);

    record OpenId(String value) {}
    final class ExchangeFailedException extends RuntimeException {
        public ExchangeFailedException() { super("Wechat OAuth code exchange failed"); }
    }
}
```

Bind properties under `app.wechat` and default each secret-bearing field to an empty string. Create the enabled provider only when `enabled`, `appId`, `appSecret`, and an HTTPS `redirectUri` are all present; otherwise return `UnavailableWechatOAuthProvider`, whose service-facing use maps to `WECHAT_LOGIN_UNAVAILABLE`. Configure a shared `OkHttpClient` with `connectTimeout(Duration.ofSeconds(5))`, `readTimeout(Duration.ofSeconds(5))`, and `callTimeout(Duration.ofSeconds(10))`.

The real provider must request only these fixed hosts:

```text
https://open.weixin.qq.com/connect/qrconnect
https://api.weixin.qq.com/sns/oauth2/access_token
```

Use `HttpUrl.Builder` for query encoding and Jackson to read `openid`/`errcode`. Never log the code, OpenID, access token, provider body, or `appSecret`.

- [ ] **Step 4: Add the environment-only configuration and deterministic test provider**

Append this shape to `application.yml`:

```yaml
app:
  wechat:
    enabled: ${WECHAT_LOGIN_ENABLED:false}
    app-id: ${WECHAT_OPEN_APP_ID:}
    app-secret: ${WECHAT_OPEN_APP_SECRET:}
    redirect-uri: ${WECHAT_OPEN_REDIRECT_URI:}
    web-public-base-url: ${WEB_PUBLIC_BASE_URL:http://localhost:5173}
```

In `TestAuthConfiguration`, provide `@Primary WechatOAuthProvider` whose `available()` returns true, `authorizeUrl(state)` returns `https://wechat.test/authorize?state=` plus the state, and `exchangeCode(code)` returns `new OpenId("test-openid-" + code)`. The test fixture must not expose app secrets to test JSON or logs.

- [ ] **Step 5: Run provider and context tests**

Run: `./mvnw -Dtest=WechatOpenPlatformProviderTest,LowCodeApplicationTest test`

Expected: PASS. The application context starts with WeChat disabled and no credentials.

## Task 4: Reuse the existing auth session contract for WeChat lookup and binding

**Files:**
- Modify: `server/src/main/java/com/example/lowcode/auth/application/AuthRepository.java`
- Modify: `server/src/main/java/com/example/lowcode/auth/infrastructure/UserMapper.java`
- Modify: `server/src/main/java/com/example/lowcode/auth/infrastructure/MyBatisAuthRepository.java`
- Modify: `server/src/main/java/com/example/lowcode/auth/application/AuthService.java`
- Modify: `server/src/main/java/com/example/lowcode/common/exception/ErrorCode.java`
- Create: `server/src/main/java/com/example/lowcode/auth/application/WechatAuthService.java`
- Create: `server/src/test/java/com/example/lowcode/auth/application/WechatAuthServiceTest.java`

- [ ] **Step 1: Write WeChat orchestration tests with mocks/fakes**

Cover the three core outcomes and identity revalidation. The test uses a fake `WechatOAuthProvider`, an in-memory state repository from Task 2, a fake `AuthRepository`, mock `RefreshTokenService`, and mock `AuditLogService`:

```java
@Test
void boundLoginIssuesExistingSessionAndRecordsRedactedAudit() {
    PreparedAuthorization authorization = service.beginLogin("/templates");
    fakeProvider.openIdForCode("ok", "openid-123");
    authRepository.addOpenId("openid-123", activeIdentity(7L, 11L, "USER"));

    CallbackResult result = service.complete("ok", authorization.state(), "browser", "127.0.0.1");

    assertThat(result.kind()).isEqualTo(CallbackResultKind.SUCCESS);
    assertThat(result.refreshToken()).isEqualTo("refresh-token");
    verify(audit).record(argThat(event -> event.action().equals("WECHAT_LOGIN")
        && !event.metadata().toString().contains("openid-123")));
}

@Test
void unknownOpenIdNeverCreatesUserOrSession() {
    PreparedAuthorization authorization = service.beginLogin("/");
    fakeProvider.openIdForCode("unknown", "openid-unknown");

    assertThat(service.complete("unknown", authorization.state(), null, null).kind())
        .isEqualTo(CallbackResultKind.UNBOUND);
    verifyNoInteractions(refreshTokenService);
}

@Test
void bindRejectsTenantThatWasDisabledAfterAuthorizationStarted() {
    PreparedAuthorization authorization = service.beginBind(new CurrentUser(7L, 11L), "/account");
    authRepository.disableTenant(11L);
    fakeProvider.openIdForCode("bind", "openid-new");

    assertThat(service.complete("bind", authorization.state(), null, null).kind())
        .isEqualTo(CallbackResultKind.FAILED);
    assertThat(authRepository.boundOpenIds()).isEmpty();
}
```

Add cases for state replay (`EXPIRED` and no provider call), disabled user, invalid role, existing binding for another user (`ALREADY_BOUND`), and idempotent binding of the same OpenID to the same user.

- [ ] **Step 2: Run the orchestration test before implementing it**

Run: `./mvnw -Dtest=WechatAuthServiceTest test`

Expected: FAIL because `WechatAuthService` is absent.

- [ ] **Step 3: Extend the repository without exposing OpenID to API DTOs**

Add these methods to `AuthRepository`:

```java
Optional<UserIdentity> findByWechatOpenId(String openId);
Optional<String> findWechatOpenIdByUserId(long userId);
boolean bindWechatOpenId(long userId, String openId);
```

`UserMapper.findByWechatOpenId` must select `id`, `phone`, `status`, and `security_version`; `MyBatisAuthRepository` must reuse its existing preferred-tenant loading to build `UserIdentity`. Implement binding as `UPDATE sys_user SET wechat_open_id = #{openId} WHERE id = #{userId} AND wechat_open_id IS NULL`; if it returns zero, read the existing OpenID for this user. Treat an equal existing value as idempotent success; otherwise map either the existing-user binding or a duplicate-key race to `WECHAT_ACCOUNT_ALREADY_BOUND`.

- [ ] **Step 4: Extract reusable active-identity/session issuance from `AuthService`**

Add package-visible methods so phone and WeChat paths share the same user/team/role checks and refresh-token issuer:

```java
AuthRepository.UserIdentity requireActiveIdentity(long userId, long tenantId) {
    AuthRepository.UserIdentity identity = authRepository.findByUserAndTenant(userId, tenantId)
        .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "登录已失效"));
    validateLoginIdentity(identity);
    validateTenantRole(identity);
    return identity;
}

LoginResult issueSession(AuthRepository.UserIdentity identity, String userAgent, String ipAddress) {
    validateLoginIdentity(identity);
    validateTenantRole(identity);
    RefreshTokenService.IssuedSession session = requireRefreshTokenService().issue(
        identity.userId(), identity.tenantId(), identity.tenantRole(), identity.securityVersion(), userAgent, ipAddress
    );
    return new LoginResult(session.accessToken(), "Bearer", session.expiresInSeconds(),
        identity.userId(), identity.tenantId(), identity.tenantRole(), session.refreshToken());
}
```

Keep `login(...)` responsible for phone-code verification and its `LOGIN` audit event, then call `issueSession`. Update `me(...)` to call `requireActiveIdentity` and return `authRepository.findWechatOpenIdByUserId(identity.userId()).isPresent()` as a new final `wechatBound` field. No OpenID ever enters `MeResult`.

- [ ] **Step 5: Implement `WechatAuthService`**

Use these service-facing records and fixed callback kinds:

```java
public record PreparedAuthorization(String authorizeUrl, String state) {}
public enum CallbackResultKind { SUCCESS, UNBOUND, ALREADY_BOUND, CANCELLED, FAILED, EXPIRED }
public record CallbackResult(CallbackResultKind kind, String returnPath, String refreshToken) {}
```

`beginLogin(returnTo)` normalizes the path, rejects unavailable provider with `WECHAT_LOGIN_UNAVAILABLE`, creates `LOGIN` state, and returns provider URL. `beginBind(currentUser, ignoredReturnTo)` calls `authService.requireActiveIdentity`, creates `BIND` state with that exact identity and fixed `/account` return path. `complete(code, state, userAgent, ip)` consumes state before provider exchange; an absent state returns `EXPIRED`. It maps blank code to `CANCELLED`, provider exchange failure to `FAILED`, unbound login to `UNBOUND`, and bound login to `SUCCESS` with `authService.issueSession(...)`. Binding rechecks `requireActiveIdentity(initiatorUserId, initiatorTenantId)` before `bindWechatOpenId`.

Record only `{ purpose, result }` in `AuditLogService.AuditEvent.metadata`; use `WECHAT_LOGIN`/`WECHAT_BIND`, `AUTH_SESSION`/`USER_IDENTITY`, and the matching success/failure outcome. Do not include `code`, `state`, OpenID, URL, access token, refresh token, user agent, or IP in audit metadata.

- [ ] **Step 6: Run focused auth tests**

Run: `./mvnw -Dtest=AuthServiceTest,WechatAuthServiceTest test`

Expected: PASS. Existing phone login tests still receive the same response shape except `me` adds the documented boolean.

## Task 5: Add the HTTP callback boundary, cookie reuse, and security rules

**Files:**
- Create: `server/src/main/java/com/example/lowcode/auth/api/AuthRefreshCookieWriter.java`
- Modify: `server/src/main/java/com/example/lowcode/auth/api/AuthController.java`
- Create: `server/src/main/java/com/example/lowcode/auth/api/WechatAuthController.java`
- Modify: `server/src/main/java/com/example/lowcode/auth/security/SecurityConfig.java`
- Modify: `server/src/main/java/com/example/lowcode/common/exception/ErrorCode.java`
- Create: `server/src/test/java/com/example/lowcode/auth/api/WechatAuthControllerTest.java`
- Modify: `server/src/test/java/com/example/lowcode/auth/api/AuthSessionSecurityTest.java`

- [ ] **Step 1: Write MockMvc/controller tests for HTTP behavior**

Write tests for public login authorization, protected bind authorization, disabled-provider `503`, and callback safety:

```java
@Test
void callbackSetsHttpOnlyCookieButNeverLeaksTokensOrOpenIdInLocation() throws Exception {
    MvcResult started = mockMvc.perform(post("/api/v1/auth/wechat/login/authorize")
            .contentType(MediaType.APPLICATION_JSON).content("{\"returnTo\":\"/templates\"}"))
        .andExpect(status().isOk())
        .andReturn();
    String state = queryParam(started.getResponse().getContentAsString(), "state");

    mockMvc.perform(get("/api/v1/auth/wechat/callback").param("code", "bound").param("state", state))
        .andExpect(status().isFound())
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("poster_refresh_token=")))
        .andExpect(header().string(HttpHeaders.LOCATION, containsString("/auth/wechat/result?result=success")))
        .andExpect(header().string(HttpHeaders.LOCATION, not(containsString("openid"))))
        .andExpect(header().string(HttpHeaders.LOCATION, not(containsString("accessToken"))));
}

@Test
void bindAuthorizeRequiresJwt() throws Exception {
    mockMvc.perform(post("/api/v1/auth/wechat/bind/authorize"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
}
```

Use a test-only provider that exposes the raw state from the returned test URL, not from a production response contract. Add direct tests that malformed `returnTo` returns `VALIDATION_ERROR`, replay redirects with `result=expired`, and the unavailable default returns `WECHAT_LOGIN_UNAVAILABLE`.

- [ ] **Step 2: Run controller tests before implementation**

Run: `./mvnw -Dtest=WechatAuthControllerTest test`

Expected: FAIL because the controller and routes are absent.

- [ ] **Step 3: Extract the shared refresh-cookie writer**

Move exact existing cookie values from `AuthController` into a component with the two operations below, then use it from all phone login, refresh, logout, and WeChat callback paths:

```java
public void write(HttpServletResponse response, String rawToken) {
    response.setHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("poster_refresh_token", rawToken)
        .httpOnly(true).secure(secureCookie).sameSite("Lax")
        .path("/api/v1/auth").maxAge(refreshTokenTtl).build().toString());
}

public void clear(HttpServletResponse response) {
    response.setHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("poster_refresh_token", "")
        .httpOnly(true).secure(secureCookie).sameSite("Lax")
        .path("/api/v1/auth").maxAge(Duration.ZERO).build().toString());
}
```

Keep the defensive blank-token check in `write` and preserve existing `AuthSessionSecurityTest` assertions.

- [ ] **Step 4: Add controller endpoints and fixed redirect construction**

Implement:

```text
POST /api/v1/auth/wechat/login/authorize  -> ApiResponse<{ authorizeUrl }>
POST /api/v1/auth/wechat/bind/authorize   -> ApiResponse<{ authorizeUrl }>, JWT required
GET  /api/v1/auth/wechat/callback          -> 302 fixed frontend result URL
```

The two POST handlers read `returnTo` only for login; binding always calls `beginBind(CurrentUser.fromJwt(jwt), "/account")`. The callback calls `WechatAuthService.complete`, writes a Cookie only for a nonblank `refreshToken`, and creates its redirect with `UriComponentsBuilder` from the configured single public base URL, path `/auth/wechat/result`, fixed `result`, and the stored normalized `returnTo`. An invalid/replayed state has no stored path and redirects to `/?result=expired` through the same result-page path with return path `/`.

Add `WECHAT_LOGIN_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "微信登录暂不可用")` and `WECHAT_ACCOUNT_ALREADY_BOUND(HttpStatus.CONFLICT, "该微信账号已绑定其他账户")` to `ErrorCode`. Let JSON authorization endpoints return those codes through the existing exception handler; callbacks always redirect fixed outcome values.

- [ ] **Step 5: Update Spring Security and API regression tests**

Permit only these unauthenticated routes before the catch-all `/api/**` matcher:

```java
.requestMatchers(HttpMethod.POST, "/api/v1/auth/wechat/login/authorize").permitAll()
.requestMatchers(HttpMethod.GET, "/api/v1/auth/wechat/callback").permitAll()
```

Do not permit `/api/v1/auth/wechat/bind/authorize`. Update `AuthSessionSecurityTest.meResolvesCurrentTenantIdentityFromDatabase` to assert `$.data.wechatBound` is `false` after phone-only signup.

- [ ] **Step 6: Run server HTTP tests**

Run: `./mvnw -Dtest=AuthSecurityTest,AuthSessionSecurityTest,WechatAuthControllerTest test`

Expected: PASS.

## Task 6: Replace the static client flow with server-authorized WeChat navigation

**Files:**
- Modify: `poster-client/src/api/types.ts`
- Modify: `poster-client/src/api/auth.ts`
- Modify: `poster-client/src/stores/session.ts`
- Modify: `poster-client/src/features/auth/LoginDialog.vue`
- Modify: `poster-client/src/features/home/WorkbenchShell.vue`
- Modify: `poster-client/src/features/account/PersonalCenter.vue`
- Create: `poster-client/src/features/auth/WechatAuthResultPage.vue`
- Modify: `poster-client/src/router/index.ts`
- Modify: `poster-client/src/api/__tests__/auth.spec.ts`
- Modify: `poster-client/src/features/auth/__tests__/LoginDialog.spec.ts`
- Modify: `poster-client/src/features/account/__tests__/PersonalCenter.spec.ts`
- Create: `poster-client/src/features/auth/__tests__/WechatAuthResultPage.spec.ts`

- [ ] **Step 1: Write API and component tests for the new browser handoff**

Add the API request expectation:

```ts
it('requests a server-generated WeChat login URL', async () => {
  fetchMock.mockResolvedValueOnce(ok({ authorizeUrl: 'https://wechat.test/authorize?state=opaque' }))

  await beginWechatLogin('/templates')

  expect(fetchMock).toHaveBeenCalledWith('/api/v1/auth/wechat/login/authorize', expect.objectContaining({
    method: 'POST', credentials: 'include', body: JSON.stringify({ returnTo: '/templates' }),
  }))
})
```

Replace the static QR expectation in `LoginDialog.spec.ts` with an emit assertion:

```ts
await user.click(screen.getByRole('button', { name: '微信登录在这里' }))
expect(emitted('wechat-login')).toEqual([[]])
expect(screen.queryByLabelText('微信登录二维码')).toBeNull()
```

Create result-page tests that mock `session.restore`: `success` calls it and replaces `/templates`; `unbound` renders “请先使用手机号登录，再绑定微信账号”; `already_bound` renders “该微信账号已绑定其他账户”; no test reads an OAuth code.

- [ ] **Step 2: Run the new frontend tests before implementation**

Run: `npm run test:unit -- src/api/__tests__/auth.spec.ts src/features/auth/__tests__/LoginDialog.spec.ts src/features/auth/__tests__/WechatAuthResultPage.spec.ts`

Expected: FAIL because the WeChat functions/page/event are absent.

- [ ] **Step 3: Add typed API and in-memory identity support**

Add these API contracts:

```ts
export interface WechatAuthorizeResult { authorizeUrl: string }
export interface CurrentIdentity { userId: number; tenantId: number; phone: string; tenantRole: TenantRole; wechatBound: boolean }
```

Implement:

```ts
export function beginWechatLogin(returnTo: string) {
  return request<WechatAuthorizeResult>('/api/v1/auth/wechat/login/authorize', {
    method: 'POST', body: JSON.stringify({ returnTo }),
  })
}
export function beginWechatBinding() {
  return request<WechatAuthorizeResult>('/api/v1/auth/wechat/bind/authorize', { method: 'POST' })
}
```

Add `wechatBound = ref<boolean | null>(null)` to the session store. `setIdentity` sets it from `CurrentIdentity`; `clear` resets it to null. Export an async `loadIdentity()` that requires the in-memory access token, calls `me(accessToken.value)`, then calls `setIdentity`. Make `restore()` use `loadIdentity()` after refresh so callback and ordinary refresh paths share the same identity mapping.

- [ ] **Step 4: Implement the dialog and workbench browser handoff**

Remove the demo QR image import, QR-only `LoginMode`, and the QR panel branch from `LoginDialog`. Add `wechat-login` to its emitted event type and emit it from both the corner entry and WeChat provider icon. Keep non-WeChat providers on the existing explicit later-phase notice.

In `WorkbenchShell`, add:

```ts
async function beginWechatLogin() {
  try {
    const { authorizeUrl } = await beginWechatLoginRequest(router.currentRoute.value.fullPath)
    window.location.assign(authorizeUrl)
  } catch (cause) {
    loginError.value = cause instanceof Error ? cause.message : '微信登录暂不可用'
  }
}
```

Alias the imported API function as `beginWechatLoginRequest` to avoid a same-name shadow. Bind it with `@wechat-login="beginWechatLogin"`. Do not manufacture a QR image or a browser token.

- [ ] **Step 5: Implement account binding and the fixed result page**

In `PersonalCenter`, call `session.loadIdentity()` on mount when `session.wechatBound === null`. Render “已绑定” only for true, “未绑定” only for false, and “加载中” for null. The binding button is enabled only for false and uses:

```ts
async function bindWechat() {
  try {
    const { authorizeUrl } = await beginWechatBinding()
    window.location.assign(authorizeUrl)
  } catch (cause) {
    wechatError.value = cause instanceof Error ? cause.message : '微信绑定暂不可用'
  }
}
```

`WechatAuthResultPage` accepts only fixed query values. On `success`, await `session.restore()` and `router.replace(safeReturnTo)` when true; on false, render “登录状态恢复失败，请重新登录”. Normalize `returnTo` client-side to a single-leading-slash path without `//`, backslashes, or a scheme, defaulting to `/`. Map all other allowed values to the fixed Chinese messages specified in the design. Never read `code`, `state`, access token, refresh token, or OpenID from the route.

Register `{ path: '/auth/wechat/result', component: () => import('@/features/auth/WechatAuthResultPage.vue') }` before no route guard; it must remain publicly reachable so callback outcomes can show even after an unbound scan.

- [ ] **Step 6: Run focused client tests and production type/build validation**

Run: `npm run test:unit -- src/api/__tests__/auth.spec.ts src/features/auth/__tests__/LoginDialog.spec.ts src/features/auth/__tests__/WechatAuthResultPage.spec.ts src/features/account/__tests__/PersonalCenter.spec.ts`

Expected: PASS.

Run: `npm run build`

Expected: PASS with no TypeScript error and no OAuth credential bundled into the client.

## Task 7: Run full verification, record the delivery, and review the diff

**Files:**
- Modify: `README.md`
- Create: `docs/progress/2026-08-19-p1-wechat-login-adapter.md`

- [ ] **Step 1: Update user-facing project status without publishing configuration secrets**

Update the P1 status sentence in `README.md` to state that WeChat website login is implemented for already-bound accounts and account binding, but real scans require the server-side `WECHAT_LOGIN_ENABLED`, AppID, AppSecret, fixed HTTPS callback, and public site base configuration. Do not add real values, QR URLs, callback codes, or OpenID examples.

- [ ] **Step 2: Write the progress record**

Create `docs/progress/2026-08-19-p1-wechat-login-adapter.md` with these exact sections:

```markdown
# P1 微信登录适配器与账号绑定

## Delivered

## Security Decisions

## Verification

## Configuration Required for Real WeChat

## Explicit Exclusions
```

Document V7 state hashing/one-time consumption, fixed callback redirect, HttpOnly Cookie reuse, no first-scan account creation, current-tenant revalidation for binding, audit metadata redaction, and the fact that real credentials were not configured or tested.

- [ ] **Step 3: Run focused and full backend verification**

Run:

```powershell
./mvnw -Dtest=WechatOAuthStateServiceTest,WechatOpenPlatformProviderTest,WechatAuthServiceTest,WechatAuthControllerTest,DatabaseMigrationIT test
./mvnw test
./mvnw -DskipTests package
```

Expected: all targeted and full backend tests PASS; Testcontainers applies Flyway V1 through V7 against MySQL.

- [ ] **Step 4: Run full frontend verification**

Run:

```powershell
npm run test:unit
npm run build
```

Expected: all Vitest files PASS and the Vite production build PASS.

- [ ] **Step 5: Inspect final scope and working-tree hygiene**

Run:

```powershell
git diff --check
git diff --stat
git status --short
```

Expected: no whitespace errors; changed files are limited to the WeChat auth adapter, V7, tests, frontend flow, README, and progress/spec/plan documents. Preserve prior P1 changes. Do not run `git add`, `git commit`, `git push`, production Flyway migration, or a real WeChat OAuth exchange without user-provided credentials.
