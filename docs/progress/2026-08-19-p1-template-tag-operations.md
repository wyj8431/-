# P1 Template Tag Operations

**Date:** 2026-08-19
**Status:** Complete

## Delivered Scope

- Added manager-protected `GET /api/v1/admin/template-tags?status=`.
- Added administrator-only `PATCH /api/v1/admin/template-tags/{code}/status`.
- Supported the existing `DRAFT`, `PUBLISHED`, and `DISABLED` tag lifecycle values.
- Validated tag codes with the same lowercase slug contract used by public template discovery.
- Recorded successful changes as `TEMPLATE_TAG_STATUS_CHANGE` audit events.
- Added `/admin/template-tags` with status filtering, administrator mutation, operator read-only state, loading/error/empty/403 states, and navigation from every admin page.
- Deliberately excluded tag creation, deletion, renaming, ordering, and template-tag association editing.

## Contract

```text
GET /api/v1/admin/template-tags?status=
PATCH /api/v1/admin/template-tags/{code}/status
{ "status": "PUBLISHED" }
```

List rows contain `id`, `code`, `name`, `sortOrder`, and `status`. Both endpoints require an authenticated tenant manager; only `ADMIN` may change status. Repeating the current status is idempotent and does not write an audit event.

## Verification

| Area | Command | Result |
| --- | --- | --- |
| Tag service unit | `server\\mvnw.cmd -Dtest=TemplateTagAdminServiceTest test` | 5 passed |
| Template/category/tag regression | `server\\mvnw.cmd -Dtest=TemplateTagAdminServiceTest,TemplateAdminServiceTest,TemplateCategoryAdminServiceTest test` | 13 passed |
| Backend packaging | `server\\mvnw.cmd -DskipTests package` | passed |
| Frontend unit | `poster-client\\npm run test:unit` | 40 passed |
| Frontend production build | `poster-client\\npm run build` | passed |
| Diff hygiene | `git diff --check` | passed; existing line-ending warnings only |

The full backend suite remains unavailable in this environment because Testcontainers cannot find a Docker daemon. Targeted unit tests, compilation, packaging, and frontend checks pass.

## Remaining P1 Gaps

- WeChat sign-in adapter and callback handling, which need a separate provider/identity-binding specification.
- Template/tag CRUD, cover/material management, and audit export.

P2 editor, template field filling, image processing, export, and free canvas remain intentionally unstarted.
