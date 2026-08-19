# P1 Template Operations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task with checkpoints.

**Goal:** Add a tenant-manager protected template operations list and an administrator-only status lifecycle without exposing or editing template schema content.

**Architecture:** Reuse the existing `design_template` table and `AuditLogService`. `DesignTemplateMapper` owns management SQL, `TemplateAdminService` owns role/status validation and audit behavior, and `AdminTemplateController` exposes the REST contract. The Vue page follows the existing members/category admin patterns and keeps operator controls read-only.

**Tech Stack:** Java 21, Spring Boot 3.5, MyBatis, JUnit 5/Mockito, Vue 3, TypeScript, Pinia, Vitest.

---

### Task 1: Add the failing backend service tests

**Files:**
- Create: `server/src/test/java/com/example/lowcode/template/application/TemplateAdminServiceTest.java`
- Reference: `server/src/test/java/com/example/lowcode/template/application/TemplateCategoryAdminServiceTest.java`

- [ ] **Step 1: Write tests for manager read access and administrator mutation.**

Use a mocked `DesignTemplateMapper`, an in-memory `AuthRepository` identity, and the existing capturing `AuditLogMapper` pattern. Cover these exact behaviors:

```java
@Test
void operatorCanListButCannotChangeTemplateStatus() { /* list returns row; change throws FORBIDDEN */ }

@Test
void administratorPublishesTemplateAndAuditsIt() { /* updateStatus(..., "PUBLISHED", ...) and audit action */ }

@Test
void publishingSetsPublishedAtAndOtherStatesClearIt() { /* verify mapper receives the requested timestamp mode */ }

@Test
void duplicateStatusIsIdempotentWithoutUpdateOrAudit() { /* same status returns view; verify no mapper update */ }

@Test
void rejectsInvalidStatusAndUnknownTemplateBeforeMutation() { /* validation and NOT_FOUND */ }
```

- [ ] **Step 2: Run the new test and verify it fails for the missing service.**

Run from `server/`:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\latest\jdk-21'
.\mvnw.cmd -Dtest=TemplateAdminServiceTest test
```

Expected: compilation failure because `TemplateAdminService` and its management mapper methods do not exist yet.

### Task 2: Implement management SQL and service behavior

**Files:**
- Modify: `server/src/main/java/com/example/lowcode/template/infrastructure/DesignTemplateMapper.java`
- Create: `server/src/main/java/com/example/lowcode/template/application/TemplateAdminService.java`
- Test: `server/src/test/java/com/example/lowcode/template/application/TemplateAdminServiceTest.java`

- [ ] **Step 1: Add the management row and mapper methods.**

Add a `findAdmin(status)` query selecting `id`, `name`, dimensions, category code, cover asset ID, featured rank, status, published time, and updated time, ordered by status, featured rank, and ID. Add `findAdminById(templateId)` without `schema_json`. Add `updateStatus(templateId, status, publishedAt)` constrained by ID. The update SQL must set `published_at` to the supplied timestamp for `PUBLISHED` and `NULL` otherwise.

- [ ] **Step 2: Implement `TemplateAdminService` with the existing manager checks.**

Normalize status to uppercase from the fixed set `{DRAFT, PUBLISHED, DISABLED}`. Resolve the current active tenant member before every operation. `list` accepts `ADMIN` and `OPERATOR`; `changeStatus` additionally requires `ADMIN`. Load the row before mutation, return `NOT_FOUND` if absent, skip the update/audit when the status is unchanged, and record:

```java
new AuditLogService.AuditEvent(
    actor.userId(), currentUser.tenantId(), "TEMPLATE_STATUS_CHANGE", "DESIGN_TEMPLATE",
    Long.toString(templateId), AuditLogService.Outcome.SUCCESS, null,
    Map.of("fromStatus", oldStatus, "toStatus", newStatus)
)
```

- [ ] **Step 3: Run the focused service tests and verify they pass.**

```powershell
$env:JAVA_HOME='C:\Program Files\Java\latest\jdk-21'
.\mvnw.cmd -Dtest=TemplateAdminServiceTest test
```

Expected: all template admin service tests pass.

### Task 3: Expose the admin API

**Files:**
- Create: `server/src/main/java/com/example/lowcode/template/api/AdminTemplateController.java`
- Modify: `server/src/test/java/com/example/lowcode/template/api/TemplateControllerTest.java` only if shared MVC setup needs an authorization assertion

- [ ] **Step 1: Add `GET /api/v1/admin/templates`.**

Accept an optional `status`, resolve `CurrentUser.fromJwt(jwt)`, and return `ApiResponse<List<TemplateAdminService.TemplateView>>` with the request trace ID.

- [ ] **Step 2: Add `PATCH /api/v1/admin/templates/{templateId}/status`.**

Accept `{ "status": "PUBLISHED" }`, pass the authenticated user to the service, and preserve the existing error envelope for `401`, `403`, `404`, and `422`.

- [ ] **Step 3: Compile the backend and rerun focused tests.**

```powershell
$env:JAVA_HOME='C:\Program Files\Java\latest\jdk-21'
.\mvnw.cmd -DskipTests compile
.\mvnw.cmd -Dtest=TemplateAdminServiceTest,TemplateCategoryAdminServiceTest test
```

Expected: compilation succeeds and both service test classes pass.

### Task 4: Add the frontend API, route, and page tests first

**Files:**
- Modify: `poster-client/src/api/types.ts`
- Modify: `poster-client/src/api/admin.ts`
- Create: `poster-client/src/features/admin/__tests__/AdminTemplatesPage.spec.ts`
- Modify: `poster-client/src/api/__tests__/admin.spec.ts`

- [ ] **Step 1: Add `TemplateAdminStatus` and `AdminTemplate` types.**

Model the server row fields exactly: numeric dimensions/ranks/IDs, nullable `categoryCode`, `coverAssetId`, `publishedAt`, and `updatedAt`.

- [ ] **Step 2: Add API functions and failing request assertions.**

Implement `loadAdminTemplates(status?, accessToken?)` and `changeAdminTemplateStatus(templateId, status, accessToken?)`. Add tests asserting encoded query parameters, `PATCH` method, JSON body, and `credentials: 'include'`.

- [ ] **Step 3: Write page tests and run them to verify the new page is missing.**

Cover administrator filtering/mutation, operator read-only state, and empty/error rendering. Run:

```powershell
cd poster-client
npm run test:unit -- src/api/__tests__/admin.spec.ts src/features/admin/__tests__/AdminTemplatesPage.spec.ts
```

Expected: the new page import fails until the component is created.

### Task 5: Implement the frontend page and navigation

**Files:**
- Create: `poster-client/src/features/admin/AdminTemplatesPage.vue`
- Modify: `poster-client/src/router/index.ts`
- Modify: `poster-client/src/features/admin/AdminShell.vue`
- Modify: `poster-client/src/features/admin/AdminMembersPage.vue`
- Modify: `poster-client/src/features/admin/AdminTemplateCategoriesPage.vue`

- [ ] **Step 1: Implement the page using the existing admin state pattern.**

Load only for `ADMIN`/`OPERATOR`; expose a status filter; render name, ID, dimensions, category, rank, published date, and status. Disable status controls for operators and while a mutation is pending. Keep `403`, API error, loading, empty, and mutation-error states explicit.

- [ ] **Step 2: Add `/admin/templates` and active navigation links.**

Keep `/admin` authentication metadata consistent with the existing admin pages. Add the templates link to every admin top navigation so the page is reachable from overview, members, and category management.

- [ ] **Step 3: Run the focused frontend tests and build.**

```powershell
npm run test:unit -- src/api/__tests__/admin.spec.ts src/features/admin/__tests__/AdminTemplatesPage.spec.ts
npm run build
```

Expected: focused tests and the production build pass.

### Task 6: Full verification and progress record

**Files:**
- Create: `docs/progress/2026-08-19-p1-template-operations.md`
- Modify: `README.md` only if the existing P1 scope sentence does not mention template operations

- [ ] **Step 1: Run the complete practical verification set.**

```powershell
# server/
$env:JAVA_HOME='C:\Program Files\Java\latest\jdk-21'
.\mvnw.cmd -DskipTests package

# poster-client/
npm run test:unit
npm run build

# repository root
git diff --check
```

- [ ] **Step 2: Record the delivered API/page, focused tests, build results, and Docker/Testcontainers limitation.**

- [ ] **Step 3: Review the diff for accidental schema exposure or editor changes.**

Run:

```powershell
git diff --stat
git diff -- server/src/main/java/com/example/lowcode/template poster-client/src/features/admin poster-client/src/api docs/progress
```

The final diff must not add schema editing, asset upload, or P2 editor routes.
