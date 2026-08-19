# P1 Template Tag Operations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task with checkpoints.

**Goal:** Add status-filtered template tag operations for tenant managers with administrator-only mutations and audit events.

**Architecture:** Extend `TemplateTagMapper`, add `TemplateTagAdminService`, and expose `AdminTemplateTagController`. The Vue page mirrors the existing category admin page and shares the established in-memory session authorization pattern.

**Tech Stack:** Java 21, Spring Boot, MyBatis, JUnit 5/Mockito, Vue 3, TypeScript, Pinia, Vitest.

---

### Task 1: Backend RED tests

**Files:**
- Create: `server/src/test/java/com/example/lowcode/template/application/TemplateTagAdminServiceTest.java`

- [ ] Write tests for manager listing, ADMIN status mutation/audit, OPERATOR rejection, invalid code/status, unknown code, and idempotent updates.
- [ ] Run `server\\mvnw.cmd -Dtest=TemplateTagAdminServiceTest test` and confirm compilation fails because the admin mapper/service do not exist.

### Task 2: Backend mapper and service

**Files:**
- Modify: `server/src/main/java/com/example/lowcode/template/infrastructure/TemplateTagMapper.java`
- Create: `server/src/main/java/com/example/lowcode/template/application/TemplateTagAdminService.java`

- [ ] Add `findAll(status)`, `findByCode(code)`, and `updateStatus(code,status)` with an `AdminTagRow` containing `id`, `code`, `name`, `sortOrder`, and `status`.
- [ ] Reuse the category service's active-member and role checks; normalize statuses and codes; map rows to `TagView`.
- [ ] On a changed status, write `TEMPLATE_TAG_STATUS_CHANGE` with `TEMPLATE_TAG` resource metadata; skip update and audit for same-status requests.
- [ ] Run the focused service test and the existing category/template service tests.

### Task 3: Backend API

**Files:**
- Create: `server/src/main/java/com/example/lowcode/template/api/AdminTemplateTagController.java`

- [ ] Add `GET /api/v1/admin/template-tags` and `PATCH /api/v1/admin/template-tags/{code}/status` using `ApiResponse`, `CurrentUser`, and `Trace-Id` exactly as existing admin controllers.
- [ ] Run `server\\mvnw.cmd -DskipTests compile` and the focused service tests.

### Task 4: Frontend RED tests and API

**Files:**
- Modify: `poster-client/src/api/types.ts`
- Modify: `poster-client/src/api/admin.ts`
- Modify: `poster-client/src/api/__tests__/admin.spec.ts`
- Create: `poster-client/src/features/admin/__tests__/AdminTemplateTagsPage.spec.ts`

- [ ] First add failing imports/assertions for `AdminTemplateTag`, `loadAdminTemplateTags`, and `changeAdminTemplateTagStatus`; run the focused Vitest command and observe missing exports/page failure.
- [ ] Add the typed status/row definitions and request functions, then make API assertions pass.

### Task 5: Frontend page and navigation

**Files:**
- Create: `poster-client/src/features/admin/AdminTemplateTagsPage.vue`
- Modify: `poster-client/src/router/index.ts`
- Modify: `poster-client/src/features/admin/AdminShell.vue`
- Modify: `poster-client/src/features/admin/AdminMembersPage.vue`
- Modify: `poster-client/src/features/admin/AdminTemplatesPage.vue`
- Modify: `poster-client/src/features/admin/AdminTemplateCategoriesPage.vue`

- [ ] Implement status filtering, administrator mutation, operator read-only state, and all loading/error/empty/403 feedback.
- [ ] Add `/admin/template-tags` and navigation links from every admin page.
- [ ] Run focused tests, `npm run test:unit`, and `npm run build`.

### Task 6: Verification and record

**Files:**
- Create: `docs/progress/2026-08-19-p1-template-tag-operations.md`
- Modify: `README.md`

- [ ] Run backend packaging, frontend unit/build checks, and `git diff --check`.
- [ ] Document the delivered API/page, test counts, Docker limitation, and remaining P1 gaps.
- [ ] Verify no tag CRUD, editor, schema, or asset upload behavior entered this slice.
