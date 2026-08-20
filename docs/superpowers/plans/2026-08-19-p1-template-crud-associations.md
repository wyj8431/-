# P1 Template CRUD And Tag Associations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add administrator template create, metadata edit, strict delete, and full replacement of template-tag associations without entering the P2 editor or asset upload scope.

**Architecture:** Reuse the existing `design_template` and `template_tag_relation` tables; no migration is required. The service owns validation, role checks, generated starter schema, reference checks, association replacement, and audit events. The existing admin template page gains local dialog state and uses the existing category/tag read APIs to render typed selectors.

**Tech Stack:** Java 21, Spring Boot, MyBatis annotations, MySQL/Flyway, Vue 3, TypeScript, Pinia, Vitest, Testing Library.

---

## Approved Contract

- `POST /api/v1/admin/templates` accepts `name`, `width`, `height`, optional `categoryCode`, optional `tagCodes`, and optional `featuredRank`; the server creates a valid empty schema and returns a `DRAFT` template.
- `PATCH /api/v1/admin/templates/{id}` updates the same metadata fields and replaces all tag relations with the supplied `tagCodes`.
- `DELETE /api/v1/admin/templates/{id}` physically deletes only an unreferenced template. Any design document, home topic, or tag relation reference returns `409 TEMPLATE_IN_USE` without unlinking anything.
- Only `ADMIN` may write. `OPERATOR` remains read-only. Every successful create, update, association replacement, or delete writes an audit event.
- Schema editing, status transitions, cover upload, and cover lifecycle remain outside this slice.

## File Structure

- Backend API: `server/src/main/java/com/example/lowcode/template/api/AdminTemplateController.java`
- Backend service: `server/src/main/java/com/example/lowcode/template/application/TemplateAdminService.java`
- Backend persistence: `server/src/main/java/com/example/lowcode/template/infrastructure/DesignTemplateMapper.java` and a focused tag-association mapper method in `TemplateTagMapper.java`
- Backend contracts: `server/src/main/java/com/example/lowcode/common/exception/ErrorCode.java`
- Backend tests: `server/src/test/java/com/example/lowcode/template/application/TemplateAdminServiceTest.java` and `server/src/test/java/com/example/lowcode/integration/DatabaseMigrationIT.java` only when a schema assertion is needed
- Frontend contracts: `poster-client/src/api/types.ts`, `poster-client/src/api/admin.ts`, `poster-client/src/api/__tests__/admin.spec.ts`
- Frontend page: `poster-client/src/features/admin/AdminTemplatesPage.vue`
- Frontend tests: `poster-client/src/features/admin/__tests__/AdminTemplatesPage.spec.ts`
- Delivery record: `docs/progress/2026-08-19-p1-template-crud-associations.md`

### Task 1: Define Failing Backend Contract Tests

**Files:**
- Modify: `server/src/test/java/com/example/lowcode/template/application/TemplateAdminServiceTest.java`

- [x] **Step 1: Add administrator create test**

Assert that a normalized administrator request inserts a row with a generated starter schema, `DRAFT` status, supplied category/recommendation values, supplied tag codes, and a `TEMPLATE_CREATE` audit event.

- [x] **Step 2: Add update and association replacement test**

Assert that update loads the existing row, validates category and tags, updates metadata, deletes old relations, inserts the new distinct tag IDs, and writes a `TEMPLATE_UPDATE` audit event.

- [x] **Step 3: Add strict delete and authorization tests**

Assert that an unreferenced administrator delete succeeds, a referenced template throws `TEMPLATE_IN_USE` without delete/unlink calls, and an operator is rejected before mapper access.

- [x] **Step 4: Run the focused test red**

Run from `server/`:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\latest\jdk-21'
.\mvnw.cmd '-Dtest=TemplateAdminServiceTest' test
```

Expected: compilation failures for the missing create/update/delete service, mapper methods, and request contracts.

### Task 2: Implement Backend Mapper, Service, and Controller

**Files:**
- Modify: `server/src/main/java/com/example/lowcode/template/infrastructure/DesignTemplateMapper.java`
- Modify: `server/src/main/java/com/example/lowcode/template/infrastructure/TemplateTagMapper.java`
- Modify: `server/src/main/java/com/example/lowcode/template/application/TemplateAdminService.java`
- Modify: `server/src/main/java/com/example/lowcode/template/api/AdminTemplateController.java`
- Modify: `server/src/main/java/com/example/lowcode/common/exception/ErrorCode.java`

- [x] **Step 1: Add persistence operations**

Add generated-key insert, metadata update, reference count, delete, current tag lookup, relation delete, and batch relation insert methods. Keep all SQL parameterized and use existing column names.

- [x] **Step 2: Add typed service commands**

Normalize names, dimensions, optional category code, distinct tag codes, and featured rank. Require active `ADMIN`; verify referenced category/tag rows exist; generate a schema object containing `schemaVersion: 1`, matching canvas dimensions, one page, and an empty elements array. Replace associations in one transaction and return an `AdminTemplate` view with `tagCodes`.

- [x] **Step 3: Add strict reference handling**

Count `design_document`, `home_topic_template`, and `template_tag_relation` references before delete. Throw `TEMPLATE_IN_USE` on any reference and do not mutate relations. Preserve `DISABLED` as the non-destructive alternative through the existing status endpoint.

- [x] **Step 4: Expose REST methods**

Add `POST`, `PATCH /{templateId}`, and `DELETE /{templateId}` methods using the existing `ApiResponse`, `CurrentUser`, trace ID, and request records. Keep status changes on the existing dedicated endpoint.

- [x] **Step 5: Run focused backend tests green**

Run:

```powershell
.\mvnw.cmd '-Dtest=TemplateAdminServiceTest,TemplateCategoryAdminServiceTest,TemplateTagAdminServiceTest' test
```

Expected: all focused service tests pass.

### Task 3: Add Typed Frontend API Contracts

**Files:**
- Modify: `poster-client/src/api/types.ts`
- Modify: `poster-client/src/api/admin.ts`
- Modify: `poster-client/src/api/__tests__/admin.spec.ts`

- [x] **Step 1: Add red request tests**

Queue successful envelopes and assert exact `POST`, `PATCH`, and `DELETE` requests, including `tagCodes`, `categoryCode`, `featuredRank`, and URL encoding.

- [x] **Step 2: Add request and response types**

Extend `AdminTemplate` with `tagCodes: string[]`; add create/update input interfaces and typed helpers returning `AdminTemplate` or `null`.

- [x] **Step 3: Run API tests green**

Run `npm run test:unit -- src/api/__tests__/admin.spec.ts` from `poster-client/` and expect all API contract tests to pass.

### Task 4: Add Administrator Template Dialogs

**Files:**
- Modify: `poster-client/src/features/admin/AdminTemplatesPage.vue`
- Modify: `poster-client/src/features/admin/__tests__/AdminTemplatesPage.spec.ts`

- [x] **Step 1: Add red page tests**

Cover administrator create/edit, tag selection, strict delete confirmation and conflict retention, operator read-only controls, and per-row command disabling while a request is pending.

- [x] **Step 2: Add local form state and command handlers**

Load categories and tags for administrators, validate name/dimensions before API calls, submit create/update requests, replace the returned row, and preserve rows after `TEMPLATE_IN_USE` failures.

- [x] **Step 3: Render accessible dialogs and table actions**

Add icon buttons with Lucide icons and tooltips, a create/edit dialog with category selector and checkbox tag list, a strict-delete alert dialog, inline API errors, and disabled pending states. Do not add schema text editing, upload controls, or new dependencies.

- [x] **Step 4: Run page tests green**

Run `npm run test:unit -- src/features/admin/__tests__/AdminTemplatesPage.spec.ts` and expect all existing and new cases to pass.

### Task 5: Full Verification And Delivery Record

**Files:**
- Create: `docs/progress/2026-08-19-p1-template-crud-associations.md`
- Modify: `README.md`

- [x] **Step 1: Verify backend and frontend**

Run the focused backend tests, `DatabaseMigrationIT`, backend full suite, backend package, frontend full unit suite, frontend build, and `git diff --check`.

- [x] **Step 2: Record scope and remaining gaps**

Document generated starter schemas, strict template deletion, association replacement, permissions, audit actions, and explicit exclusions for schema editing, cover upload, WeChat, and P2 editor work.

- [x] **Step 3: Review final diff**

Confirm no changes to production migration execution, public discovery semantics, user design documents, authentication, or unrelated admin modules.
