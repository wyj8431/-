# P1 Audit CSV Export

**Date:** 2026-08-19
**Status:** Complete

## Delivered Scope

- Added tenant-scoped CSV export at GET /api/v1/admin/audit-logs/export.
- Kept the existing manager authorization boundary: ADMIN and OPERATOR can export their authenticated tenant's audit events; normal users cannot.
- Reused action, outcome, and ISO-8601 time-range filters and capped the export at the newest 5,000 matching rows.
- Added X-Audit-Export-Total and X-Audit-Export-Truncated response headers so clients can identify the matching count and whether the file was truncated.
- Exported only the fixed masked AuditView projection: id, actor_user_id, actor_phone_masked, action, resource_type, resource_id, outcome, request_id, and created_at.
- Excluded audit metadata and sensitive values from the CSV, including tokens, cookies, verification codes, and raw phone numbers.
- Added the management-shell export control with the active audit filters, Blob download, and an inline error state.

## Contract

~~~text
GET /api/v1/admin/audit-logs/export
  ?action=LOGIN&outcome=FAILURE&from=2026-08-18T00:00:00Z&to=2026-08-19T00:00:00Z
~~~

The response is a UTF-8 CSV download named audit-logs.csv. Values are CSV-escaped, the file includes a UTF-8 BOM, and rows use the fixed masked columns above.

## Verification

| Area | Command | Result |
| --- | --- | --- |
| Backend audit unit | `server\mvnw.cmd -Dtest=AuditLogServiceTest,AuditCsvExporterTest test` | 4 passed |
| Frontend API focused | `poster-client\npm run test:unit -- src/api/__tests__/http.spec.ts src/api/__tests__/admin.spec.ts` | 10 passed |
| Frontend admin-shell focused | `poster-client\npm run test:unit -- src/features/admin/__tests__/AdminShell.spec.ts` | 7 passed |
| Frontend unit suite | `poster-client\npm run test:unit` | 16 files / 44 passed |
| Frontend production build | `poster-client\npm run build` | passed |
| Backend packaging | `server\mvnw.cmd -DskipTests package` | passed |
| Diff hygiene | `git diff --check` | passed; existing LF/CRLF warnings only |

The full backend integration suite remains unavailable in this environment because Testcontainers cannot reach a Docker daemon. The focused unit results above do not require Docker.

## Remaining P1 Gaps

- WeChat sign-in adapter and callback handling.
- Template creation/editing, tag CRUD, and cover and asset management.
- P2 editor, schema, canvas, image-upload, and export work remain intentionally outside this change.
