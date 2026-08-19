# P1 Session And Admin Foundation

**Date:** 2026-08-18
**Status:** Complete

## Delivered Scope

- Added Flyway V4 for refresh-token state, audit events, and `sys_user.security_version`.
- Added opaque 32-byte refresh tokens stored only as SHA-256 hashes. Tokens rotate on refresh; replay revokes the complete token family.
- Added login Cookie issuance, refresh, logout, and database-backed current identity APIs. The browser receives `poster_refresh_token` as an `HttpOnly`, `SameSite=Lax` Cookie; the access token remains in memory only.
- Added `ADMIN`, `OPERATOR`, and `USER` tenant roles to Spring Security and to the browser session identity.
- Added protected admin summary and administrator-only tenant-role mutation APIs, with audit events, target-session revocation, and a final-administrator downgrade guard.
- Added browser session restore, one shared refresh request for concurrent calls, one retry after a 401 response, preserved navigation intent, and local identity cleanup when server-side logout is unavailable.
- Added the `/admin` management shell with loading, summary, retryable error, and in-page 403 states. `ADMIN` and `OPERATOR` receive a **团队管理** account-menu entry; `USER` does not.

## Security Decisions

- Refresh-token plaintext never enters the database, browser storage, audit metadata, or API response body.
- Role checks are enforced by server endpoints; hiding the management entry is only a user-interface choice.
- A tenant-role change invalidates the target member's refresh sessions for that tenant. Short-lived access tokens can remain valid only until their existing expiry.
- Local logout clears browser memory even if the revocation call cannot reach the server, preventing an unhandled browser error and preserving the user's immediate sign-out intent.

## Verification

| Area | Command | Result |
| --- | --- | --- |
| Backend unit and integration | `server\\mvnw.cmd test` with JDK 21 | 66 passed |
| Database migration | `server\\mvnw.cmd -Dtest=DatabaseMigrationIT test` with JDK 21 | 5 passed |
| Frontend unit | `poster-client\\npm run test:unit` | 19 passed |
| Frontend production build | `poster-client\\npm run build` | passed |
| Browser regression | `poster-client\\npm run test:e2e` | 5 passed, 1 desktop-only case skipped |

## Remaining P1 Gaps

- WeChat sign-in adapter and callback handling.
- Tenant-member, template, category, and asset management screens.
- Auditable event query, filtering, and detailed management workflows.
