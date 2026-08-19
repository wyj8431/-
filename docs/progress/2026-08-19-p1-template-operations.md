# P1 Template Operations

**Date:** 2026-08-19
**Status:** Complete

## Delivered Scope

- Added manager-protected `GET /api/v1/admin/templates?status=` for all template operation rows.
- Added administrator-only `PATCH /api/v1/admin/templates/{templateId}/status`.
- Supported the existing `DRAFT`, `PUBLISHED`, and `DISABLED` lifecycle values.
- Kept `schema_json` and template field content out of the management list response.
- Set `published_at` when a template is published and clear it when moved back to draft or disabled.
- Recorded successful status changes as `TEMPLATE_STATUS_CHANGE` audit events with the old and new status.
- Added `/admin/templates` with status filtering, admin mutation, operator read-only state, loading/error/empty/403 states, and navigation links.

## Contract

```text
GET /api/v1/admin/templates?status=
PATCH /api/v1/admin/templates/{templateId}/status
{ "status": "PUBLISHED" }
```

List rows contain `id`, `name`, `width`, `height`, `categoryCode`, `coverAssetId`, `featuredRank`, `status`, `publishedAt`, and `updatedAt`. Both endpoints require an authenticated tenant manager; only `ADMIN` may change status. Templates remain global operation resources; tenant role checks are the authorization boundary.

## Verification

| Area | Command | Result |
| --- | --- | --- |
| Template service unit | `server\\mvnw.cmd -Dtest=TemplateAdminServiceTest test` | 5 passed |
| Template/category regression | `server\\mvnw.cmd -Dtest=TemplateAdminServiceTest,TemplateCategoryAdminServiceTest test` | 8 passed |
| Backend packaging | `server\\mvnw.cmd -DskipTests package` | passed |
| Frontend unit | `poster-client\\npm run test:unit` | 37 passed |
| Frontend production build | `poster-client\\npm run build` | passed |
| Diff hygiene | `git diff --check` | passed; existing line-ending warnings only |

The full backend suite remains unavailable in this environment because Testcontainers cannot find a Docker daemon. Targeted unit tests, compilation, packaging, and frontend checks pass.

## Remaining P1 Gaps

- WeChat sign-in adapter and callback handling, which need a separate provider/identity-binding specification.
- Template creation/editing, tag management, cover/material management, and audit export.

P2 editor, template field filling, image processing, export, and free canvas remain intentionally unstarted.
