# P1 Audit Export Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task with checkpoints.

**Goal:** Add a bounded, tenant-scoped CSV export for the existing audit filters and expose it from the admin audit panel.

**Architecture:** `AuditLogService` performs bounded filtered reads from the existing mapper and returns already masked `AuditView` records. `AuditCsvExporter` owns CSV formatting; `AdminController` returns the file with truncation headers. The frontend HTTP layer downloads a Blob with the same one-refresh retry behavior as JSON requests.

**Tech Stack:** Java 21, Spring Boot, MyBatis, JUnit 5, Vue 3, TypeScript, Vitest.

---

### Task 1: Backend RED tests

**Files:**
- Modify: `server/src/test/java/com/example/lowcode/audit/application/AuditLogServiceTest.java`
- Create: `server/src/test/java/com/example/lowcode/audit/application/AuditCsvExporterTest.java`

- [ ] Add failing tests for normalized export filters, a `5000` mapper limit, total/truncated metadata, and CSV quoting/BOM/header output.
- [ ] Run `server\\mvnw.cmd -Dtest=AuditLogServiceTest,AuditCsvExporterTest test` and confirm missing methods/classes fail compilation.

### Task 2: Backend bounded export and CSV writer

**Files:**
- Modify: `server/src/main/java/com/example/lowcode/audit/application/AuditLogService.java`
- Create: `server/src/main/java/com/example/lowcode/audit/application/AuditCsvExporter.java`

- [ ] Add `MAX_EXPORT_ROWS = 5000`, `AuditExportQuery`, and `AuditExport` records. Reuse existing action/outcome/time normalization and call `findPage(..., 5000, 0)` plus `countPage`.
- [ ] Add a Spring `AuditCsvExporter` that writes UTF-8 BOM, fixed headers, quoted/escaped fields, masked values only, and CRLF rows.
- [ ] Run focused audit tests and existing audit/auth tests.

### Task 3: Backend controller/service contract

**Files:**
- Modify: `server/src/main/java/com/example/lowcode/admin/application/AdminService.java`
- Modify: `server/src/main/java/com/example/lowcode/admin/api/AdminController.java`

- [ ] Add manager-protected `auditLogExport(CurrentUser, AuditExportQuery)`.
- [ ] Add `GET /api/v1/admin/audit-logs/export` returning `ResponseEntity<byte[]>`, `text/csv`, `Content-Disposition`, total, and truncation headers.
- [ ] Compile and rerun focused tests.

### Task 4: Frontend RED tests and download helper

**Files:**
- Modify: `poster-client/src/api/http.ts`
- Modify: `poster-client/src/api/admin.ts`
- Modify: `poster-client/src/api/__tests__/admin.spec.ts`
- Modify: `poster-client/src/api/__tests__/http.spec.ts`

- [ ] Add failing tests for Blob download, encoded audit filters, and one refresh retry.
- [ ] Implement `download(path, init, accessToken)` in the HTTP layer and `downloadAdminAuditLogs(params, accessToken)` in the admin API.
- [ ] Run focused tests and verify they pass.

### Task 5: Add the admin export control

**Files:**
- Modify: `poster-client/src/features/admin/AdminShell.vue`
- Modify: `poster-client/src/features/admin/__tests__/AdminShell.spec.ts`

- [ ] Add an icon+text export button next to audit filters, loading state, and inline failure message.
- [ ] Pass current filters, create a temporary object URL for the Blob, trigger `audit-logs.csv`, and revoke the URL.
- [ ] Test successful invocation and failed export feedback.

### Task 6: Verification and record

**Files:**
- Create: `docs/progress/2026-08-19-p1-audit-export.md`
- Modify: `README.md`

- [ ] Run focused/full frontend tests, frontend build, backend package, and `git diff --check`.
- [ ] Record the 5,000-row bound, masking guarantee, tests, and Docker limitation.
- [ ] Confirm no audit metadata or editor functionality entered the change.
