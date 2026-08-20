# P1 Template Tag CRUD

**Date:** 2026-08-19
**Status:** Complete

## Delivered Scope

- Added administrator-only `POST /api/v1/admin/template-tags` with normalized code/name/sort order and a fixed initial `DRAFT` status.
- Added administrator-only `PATCH /api/v1/admin/template-tags/{code}` for name and sort-order edits; tag codes remain immutable.
- Added administrator-only `DELETE /api/v1/admin/template-tags/{code}` with strict deletion: referenced tags are not changed and return `409 TEMPLATE_TAG_IN_USE`.
- Kept `OPERATOR` access read-only and rechecked active user, tenant, and membership state on every write.
- Added `TEMPLATE_TAG_CREATE`, `TEMPLATE_TAG_UPDATE`, and `TEMPLATE_TAG_DELETE` audit events using the existing metadata sanitization rules.
- Added V5 generated-ID migration. MySQL temporarily drops and recreates the unchanged tag-relation foreign key around the `AUTO_INCREMENT` alteration; relation rows and the final FK contract are preserved.
- Added administrator create/edit/delete dialogs, accessible icon actions, confirmation flow, validation feedback, and in-use conflict messaging to `/admin/template-tags`.

## Contract

```text
POST /api/v1/admin/template-tags
{ "code": "holiday-sale", "name": "节日促销", "sortOrder": 20 }

PATCH /api/v1/admin/template-tags/{code}
{ "name": "节日活动", "sortOrder": 30 }

DELETE /api/v1/admin/template-tags/{code}
```

Create and update accept codes matching `[a-z0-9-]{1,64}`, trimmed names of length `1..128`, and integer sort orders in `0..100000`. Delete returns `{ "code": "OK", "data": null }` on success. Referenced tags are never auto-unlinked; operators can disable them through the existing status control.

## Verification

| Area | Command | Result |
| --- | --- | --- |
| Migration integration | `server\\mvnw.cmd -Dtest=DatabaseMigrationIT test` | 8 passed; V5 applied in MySQL and FK/data assertions passed |
| Backend CRUD regression | `server\\mvnw.cmd -Dtest=TemplateTagAdminServiceTest,TemplateAdminServiceTest,TemplateCategoryAdminServiceTest test` | 20 passed |
| Frontend API | `poster-client\\npm run test:unit -- src/api/__tests__/admin.spec.ts` | 8 passed |
| Frontend tag page | `poster-client\\npm run test:unit -- src/features/admin/__tests__/AdminTemplateTagsPage.spec.ts` | 6 passed |
| Frontend unit suite | `poster-client\\npm run test:unit` | 16 files / 49 passed |
| Frontend production build | `poster-client\\npm run build` | passed |
| Backend packaging | `server\\mvnw.cmd -DskipTests package` | passed |
| Diff hygiene | `git diff --check` | passed; existing LF/CRLF warnings only |

The full backend suite passed with 92 tests. Production migration execution remains outside this task and requires the normal release review.

## Remaining P1 Gaps

- WeChat sign-in adapter and callback handling.
- Template creation/editing, template-tag association editing, cover/material management.

P2 editor, schema, canvas, image upload, and export remain intentionally outside this change.
