# P1 Template Category Management

**Date:** 2026-08-19
**Status:** Complete

## Delivered Scope

- Added tenant-manager protected `GET /api/v1/admin/template-categories`, with an optional category-status filter.
- Added administrator-only `PATCH /api/v1/admin/template-categories/{code}/status` for existing categories.
- Supported the existing category lifecycle values: `DRAFT`, `PUBLISHED`, and `DISABLED`.
- Allowed `ADMIN` and `OPERATOR` to view categories while keeping status changes exclusive to `ADMIN`.
- Validated category codes and requested status values before a write.
- Recorded each successful status change as `TEMPLATE_CATEGORY_STATUS_CHANGE`, including the prior and resulting status.
- Added `/admin/template-categories` with status filtering, read-only operator state, mutation/error/loading/empty states, and navigation from the administration shell.

## Contract

```text
GET /api/v1/admin/template-categories?status=
PATCH /api/v1/admin/template-categories/{code}/status
{ "status": "PUBLISHED" }
```

The list returns each category's `id`, `code`, `name`, `parentCode`, `sortOrder`, and `status`. The status change returns the updated category. Both endpoints require an authenticated tenant manager; the mutation endpoint requires `ADMIN`.

## Verification

| Area | Command | Result |
| --- | --- | --- |
| Category service unit | `server\\mvnw.cmd -Dtest=TemplateCategoryAdminServiceTest test` | 3 passed |
| Backend packaging | `server\\mvnw.cmd -DskipTests package` | passed |
| Frontend unit | `poster-client\\npm run test:unit` | 34 passed |
| Frontend production build | `poster-client\\npm run build` | passed |
| Diff hygiene | `git diff --check` | passed; existing line-ending warnings only |

The full backend suite remains unavailable in this environment because Testcontainers cannot find a Docker daemon. This is an environment limitation; targeted unit tests, compilation, packaging, and frontend checks pass.

## Remaining P1 Gaps

- WeChat sign-in adapter and callback handling.
- Template CRUD, tag management, cover/material management, and asset lifecycle workflows.
- Audit export and broader ToB operational workflows.
