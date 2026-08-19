# P1 Session and Admin Foundation Implementation Plan

> **For agentic workers:** Execute this plan task-by-task with test-first development and verification checkpoints.

**Goal:** Add revocable rotating refresh sessions, tenant-role protected identity APIs, and the smallest usable `/admin` shell while preserving the completed P0 workbench.

**Architecture:** Keep access tokens as short-lived HS256 JWTs held only in browser memory. Store only SHA-256 hashes of opaque refresh tokens in MySQL, rotate tokens in a transaction, and revoke a token family on replay. Resolve the current tenant member from the database for `/me` and admin actions; use JWT tenant-role claims only for coarse Spring Security route checks. The Vue client owns one in-memory session store and a deduplicated refresh promise, while the HTTP wrapper retries a 401 request once.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Security resource server, MyBatis-Plus annotations, Flyway/MySQL, Vue 3, TypeScript, Pinia, Vue Router, Vitest, MockMvc/Testcontainers.

---

### Task 1: Persist session and audit state

**Files:**
- Create: `server/src/main/resources/db/migration/V4__auth_refresh_tokens_and_audit.sql`
- Test: `server/src/test/java/com/example/lowcode/integration/DatabaseMigrationIT.java`

- [ ] Add `security_version` to `sys_user`, create `auth_refresh_token` and `sys_audit_log` with the constraints and indexes from the approved P1 specification.
- [ ] Extend the migration integration assertions to verify the new columns, tables, unique token hash, and audit outcome constraint.
- [ ] Run `mvn -f server/pom.xml -Dtest=DatabaseMigrationIT test` with `JAVA_HOME=C:\Program Files\Java\latest\jdk-21` and confirm the new migration applies on MySQL 8.4.

### Task 2: Implement rotating refresh-token application services

**Files:**
- Create: `server/src/main/java/com/example/lowcode/auth/application/RefreshTokenRepository.java`
- Create: `server/src/main/java/com/example/lowcode/auth/application/RefreshTokenService.java`
- Create: `server/src/main/java/com/example/lowcode/auth/infrastructure/RefreshTokenMapper.java`
- Create: `server/src/main/java/com/example/lowcode/audit/application/AuditLogService.java`
- Create: `server/src/main/java/com/example/lowcode/audit/infrastructure/AuditLogMapper.java`
- Test: `server/src/test/java/com/example/lowcode/auth/application/RefreshTokenServiceTest.java`
- Test: `server/src/test/java/com/example/lowcode/audit/application/AuditLogServiceTest.java`

- [ ] Write failing tests for token issuance, one-time rotation, expiry/revocation, family-wide replay revocation, and redaction of sensitive audit metadata.
- [ ] Implement a `SecureRandom` 32-byte opaque token, constant-time hash comparison, transactionally locked replacement, user/tenant/security-version checks, and explicit revoke reasons.
- [ ] Record login, refresh, logout, replay, and role-change events through a small structured audit service that never stores tokens, cookies, full phones, or verification codes.
- [ ] Run the focused Maven tests and then the existing auth unit tests.

### Task 3: Wire auth HTTP contracts and tenant-role authorization

**Files:**
- Modify: `server/src/main/java/com/example/lowcode/auth/application/AuthService.java`
- Modify: `server/src/main/java/com/example/lowcode/auth/api/AuthController.java`
- Modify: `server/src/main/java/com/example/lowcode/auth/security/JwtTokenService.java`
- Modify: `server/src/main/java/com/example/lowcode/auth/security/CurrentUser.java`
- Modify: `server/src/main/java/com/example/lowcode/auth/security/SecurityConfig.java`
- Create: `server/src/main/java/com/example/lowcode/admin/api/AdminController.java`
- Create: `server/src/main/java/com/example/lowcode/admin/application/AdminService.java`
- Create: `server/src/main/java/com/example/lowcode/admin/infrastructure/AdminMapper.java`
- Test: `server/src/test/java/com/example/lowcode/auth/api/AuthSessionSecurityTest.java`
- Test: `server/src/test/java/com/example/lowcode/admin/api/AdminAuthorizationTest.java`

- [ ] Add refresh-cookie issuance to login, `POST /auth/refresh`, idempotent `POST /auth/logout`, and database-backed `GET /auth/me`; missing/invalid cookies return 401 and clear the cookie.
- [ ] Map `ADMIN`, `OPERATOR`, and `USER` to `ROLE_ADMIN`, `ROLE_OPERATOR`, and `ROLE_USER`; protect `/api/v1/admin/**` and preserve the independent design ACL checks.
- [ ] Add `GET /api/v1/admin/summary` and `PATCH /api/v1/admin/users/{userId}/tenant-role`, including the last-admin downgrade guard and target-session revocation.
- [ ] Verify login/refresh/logout/me, 401/403 behavior, role mutation, final-admin protection, replay handling, and audit events with MockMvc/Testcontainers.

### Task 4: Add in-memory browser session recovery

**Files:**
- Modify: `poster-client/src/api/types.ts`
- Modify: `poster-client/src/api/auth.ts`
- Modify: `poster-client/src/api/http.ts`
- Modify: `poster-client/src/stores/session.ts`
- Modify: `poster-client/src/router/index.ts`
- Modify: `poster-client/src/main.ts`
- Test: `poster-client/src/stores/__tests__/session.spec.ts`
- Create: `poster-client/src/api/__tests__/http.spec.ts`

- [ ] Write failing Vitest cases for restore, concurrent refresh deduplication, one retry after 401, failed-refresh cleanup, and pending-intent preservation.
- [ ] Implement `credentials: 'include'`, a single refresh promise, in-memory token state, `session.restore()`, and a one-retry HTTP wrapper that clears state on refresh failure.
- [ ] Add route metadata for authenticated/admin roles and a guard that sends unauthenticated users to `/` with `returnTo`, while showing 403 for `USER`.
- [ ] Run all frontend unit tests and the production build.

### Task 5: Deliver the minimal admin shell

**Files:**
- Create: `poster-client/src/features/admin/AdminShell.vue`
- Create: `poster-client/src/api/admin.ts`
- Modify: `poster-client/src/router/index.ts`
- Modify: `poster-client/src/App.vue`
- Test: `poster-client/src/features/admin/__tests__/AdminShell.spec.ts`

- [ ] Add loading, successful summary, 403, and retryable API-error states with current phone/tenant role and health/audit count.
- [ ] Ensure `ADMIN` and `OPERATOR` enter `/admin`, `USER` receives an in-page 403, and unauthenticated navigation preserves `returnTo=/admin`.
- [ ] Run unit, build, and existing Playwright checks; document the new local flow in `README.md`.

### Task 6: Final verification and stage record

**Files:**
- Modify: `README.md`
- Create: `docs/progress/2026-08-18-p1-session-admin-foundation.md`

- [ ] Run backend unit/integration tests, frontend unit/build/E2E tests, and inspect the final diff for accidental changes to the existing homepage styling.
- [ ] Record implemented scope, test results, security decisions, and remaining P1 gaps (WeChat adapter, full management CRUD, audit query UI).
