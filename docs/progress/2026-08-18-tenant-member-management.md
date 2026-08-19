# P1 Tenant Member Management

**Date:** 2026-08-18
**Status:** Complete

## Delivered Scope

- Added tenant-scoped, paginated member listing at `GET /api/v1/admin/users`.
- Added role and user-status filters with stable `created_at DESC, id DESC` ordering.
- Returned only server-generated masked phone numbers; no display name, avatar, or raw phone fields are exposed.
- Restricted member listing to `ADMIN` and `OPERATOR` tenant roles.
- Restricted tenant-role mutation to `ADMIN`; `OPERATOR` is read-only.
- Preserved final-administrator downgrade protection, target session revocation, security-version invalidation, and `ROLE_CHANGE` audit records.
- Added the management-shell member table with loading, empty, error/retry, disabled-member, and role mutation states.
- Added migration assertions for the `ADMIN / USER / OPERATOR` role set without changing published migrations.

## Contract

```text
GET /api/v1/admin/users?page=1&pageSize=20&role=&status=
PATCH /api/v1/admin/users/{userId}/tenant-role
```

Member rows contain `userId`, `phoneMasked`, `tenantRole`, `userStatus`, and `joinedAt`. All reads and writes are constrained by the current tenant derived from the authenticated session.

## Verification

| Area | Command | Result |
| --- | --- | --- |
| Backend full suite | `server\\mvnw.cmd test` with JDK 21 | 69 passed |
| Frontend unit | `poster-client\\npm run test:unit` | 27 passed |
| Frontend production build | `poster-client\\npm run build` | passed |
| Browser regression | `poster-client\\npm run test:e2e` | 9 passed, 1 configured skip |
| Diff hygiene | `git diff --check` | passed; existing line-ending warnings only |

## Next P1 Gaps

- WeChat sign-in adapter and callback handling.
- Template, category, and asset management workflows.
- Auditable event query and filtering.
- Role management page and broader ToB navigation.
