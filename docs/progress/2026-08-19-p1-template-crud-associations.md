# P1 Template CRUD And Tag Associations

**Date:** 2026-08-19  
**Status:** Complete

## Delivered Scope

- Added administrator-only `POST /api/v1/admin/templates` for normalized template metadata and a generated valid empty starter schema.
- Added administrator-only `PATCH /api/v1/admin/templates/{templateId}` for metadata edits and full replacement of template-tag associations.
- Added administrator-only `DELETE /api/v1/admin/templates/{templateId}` with strict reference checks. Referenced templates return `409 TEMPLATE_IN_USE` and are not unlinked.
- Kept `OPERATOR` read-only for template create, edit, delete, and status operations; manager access remains available for listing.
- Added `TEMPLATE_CREATE`, `TEMPLATE_UPDATE`, `TEMPLATE_TAG_ASSOCIATIONS_REPLACE`, and `TEMPLATE_DELETE` audit events.
- Extended `AdminTemplate` responses with `tagCodes`; category and tag selectors reuse existing admin resource APIs.
- Added administrator create/edit dialogs, checkbox tag selection, accessible icon actions, strict-delete confirmation, pending states, validation, and in-use conflict feedback to `/admin/templates`.
- Reused the existing `design_template` and `template_tag_relation` tables. No production migration was added or executed.

## Contract

```text
POST /api/v1/admin/templates
{ "name": "节日促销", "width": 1080, "height": 1440,
  "categoryCode": "marketing", "tagCodes": ["promotion"], "featuredRank": 20 }

PATCH /api/v1/admin/templates/{templateId}
{ "name": "节日活动", "width": 1200, "height": 1600,
  "categoryCode": "marketing", "tagCodes": ["seasonal"], "featuredRank": 30 }

DELETE /api/v1/admin/templates/{templateId}
```

Create starts in `DRAFT` and generates a schema with `schemaVersion: 1`, matching canvas dimensions, one page, and no elements. Category and tag resources are validated by code regardless of their operational status. Delete checks design documents, template fields, tag relations, and home-topic references before removing anything.

## Verification

| Area | Command | Result |
| --- | --- | --- |
| Backend focused services | `server\\mvnw.cmd -Dtest=TemplateAdminServiceTest,TemplateCategoryAdminServiceTest,TemplateTagAdminServiceTest test` | 25 passed |
| Backend full suite | `server\\mvnw.cmd test` | 106 tests passed |
| Migration integration | `server\\mvnw.cmd -Dtest=DatabaseMigrationIT test` | 8 passed |
| Frontend API | `poster-client\\npm run test:unit -- src/api/__tests__/admin.spec.ts` | 9 passed |
| Frontend template page | `poster-client\\npm run test:unit -- src/features/admin/__tests__/AdminTemplatesPage.spec.ts` | 4 passed |
| Frontend unit suite | `poster-client\\npm run test:unit` | 16 files / 52 passed |
| Frontend production build | `poster-client\\npm run build` | passed |
| Backend packaging | `server\\mvnw.cmd -DskipTests package` | passed |
| Diff hygiene | `git diff --check` | passed; line-ending warnings only |

No `git add`, `git commit`, or `git push` was performed.

## Remaining P1 Gaps

- WeChat sign-in adapter and callback handling.
- Cover upload and material lifecycle management.

P2 editor, schema editing, canvas interactions, field filling, image processing, export, and free canvas remain intentionally outside this change.
