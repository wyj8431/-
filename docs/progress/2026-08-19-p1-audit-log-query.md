# P1 Audit Log Query

**Date:** 2026-08-19
**Status:** Complete

## Delivered Scope

- Added tenant-scoped, paginated `GET /api/v1/admin/audit-logs`.
- Added exact action, outcome, and ISO-8601 time-range filters with bounded page sizes.
- Kept authorization in the existing admin service boundary: `ADMIN` and `OPERATOR` can read audit records; normal users cannot.
- Returned only masked actor identity and event summary fields. Sanitized `metadata_json` remains server-side and is not exposed by this endpoint.
- Added the management-shell audit table, outcome/action filters, retry state, empty state, and pagination.

## Contract

```text
GET /api/v1/admin/audit-logs?page=1&pageSize=20&action=&outcome=&from=&to=
```

Rows contain `id`, masked actor information, `action`, `resourceType`, `resourceId`, `outcome`, `requestId`, and `createdAt`. Results are ordered newest first and always constrained to the authenticated tenant.

## Verification

| Area | Command | Result |
| --- | --- | --- |
| Audit service unit | `server\\mvnw.cmd -Dtest=AuditLogServiceTest test` | 2 passed |
| Backend packaging | `server\\mvnw.cmd -DskipTests package` | passed |
| Frontend unit | `poster-client\\npm run test:unit` | 29 passed |
| Frontend production build | `poster-client\\npm run build` | passed |
| Diff hygiene | `git diff --check` | passed; existing line-ending warnings only |

The full backend suite remains unable to run in this environment because Testcontainers cannot find a Docker daemon. The failure is environmental; the non-container unit tests and compilation pass.

## Remaining P1 Gaps

- WeChat sign-in adapter and callback handling.
- Template, category, and asset management workflows.
- Broader ToB navigation and a dedicated role-management page.
