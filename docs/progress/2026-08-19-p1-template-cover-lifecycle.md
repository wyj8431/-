# P1 Template Cover Lifecycle

**Date:** 2026-08-19
**Status:** Complete

## Delivered Scope

- Added a dedicated platform cover upload lifecycle. It does not reuse the tenant `asset` table or its object-key namespace.
- Added `template_cover_upload_session` and nullable `template_cover_asset.upload_session_id` in V6. Existing cover rows remain compatible.
- Added administrator-only presign and complete endpoints at `/api/v1/admin/template-cover-assets`; the server rechecks active membership and `ADMIN` role on every write.
- Validated filename, MIME type, file size, SHA-256, image magic/decoding, dimensions, and promoted-object hash before creating a `DRAFT` cover asset.
- Added list, status, and strict-delete operations. `OPERATOR` can only list. Referenced covers return `409 TEMPLATE_COVER_IN_USE`; template and homepage references are not removed.
- Added `PATCH /api/v1/admin/templates/{templateId}/cover` for binding or clearing a cover. New bindings require `PUBLISHED` covers; an existing disabled cover can be retained while other metadata is saved.
- Added audit events: `TEMPLATE_COVER_ASSET_CREATE`, `TEMPLATE_COVER_ASSET_STATUS_CHANGE`, `TEMPLATE_COVER_ASSET_DELETE`, and `TEMPLATE_COVER_BIND`.
- Added `/admin/template-covers`, typed frontend API helpers, upload/status/delete UI, and a cover selector in the template create/edit dialog.

## Contract

```text
POST   /api/v1/admin/template-cover-assets/presign
POST   /api/v1/admin/template-cover-assets/complete
GET    /api/v1/admin/template-cover-assets?status=
PATCH  /api/v1/admin/template-cover-assets/{id}/status
DELETE /api/v1/admin/template-cover-assets/{id}
PATCH  /api/v1/admin/templates/{id}/cover
```

`presign` accepts `fileName`, `mimeType`, `fileSize`, and lowercase `sha256`. `complete` accepts `sessionId`. Binding accepts `{ "coverAssetId": number | null }`.

## Verification

| Area | Command | Result |
| --- | --- | --- |
| Backend cover service | `server\mvnw.cmd -Dtest=TemplateCoverAdminServiceTest test` | 5 passed |
| Database migration | `server\mvnw.cmd -Dtest=DatabaseMigrationIT test` | 9 passed; Flyway V1-V6 applied in MySQL 8.4 |
| Backend full suite | `server\mvnw.cmd test` | 102 passed |
| Frontend focused API/page | `poster-client\npm run test:unit -- --run src/api/__tests__/admin.spec.ts src/features/admin/__tests__/AdminTemplatesPage.spec.ts` | 15 passed |
| Frontend cover page | `poster-client\npm run test:unit -- --run src/features/admin/__tests__/AdminTemplateCoversPage.spec.ts` | 2 passed |
| Frontend unit suite | `poster-client\npm run test:unit` | 17 files / 56 passed |
| Frontend production build | `poster-client\npm run build` | passed |
| Backend packaging | `server\mvnw.cmd -DskipTests package` | passed |
| Diff hygiene | `git diff --check` | passed; line-ending warnings only |

No production migration was applied; the migration verification used an isolated Testcontainers MySQL database.

## Remaining P1 Gaps

- WeChat sign-in adapter and callback handling.
- Homepage topic management and other non-cover platform material workflows.

P2 editor, schema editing, canvas interactions, merchant image editing, export, and free canvas remain outside this change.
