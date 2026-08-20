# P1 Template Tag CRUD Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver administrator-only template tag creation, detail editing, and strict deletion while preserving template associations and public discovery behavior.

**Architecture:** Keep platform tag writes in `TemplateTagAdminService`, with MyBatis mapper methods for generated-ID insert, detail update, reference counting, and delete. The controller exposes RESTful admin endpoints in the existing `ApiResponse` envelope. The Vue admin page owns only dialog state and calls typed API helpers; the backend remains authoritative for roles, validation, and delete conflicts.

**Tech Stack:** Java 21, Spring Boot 3, MyBatis, Flyway, MySQL, JUnit 5, Mockito, Vue 3, TypeScript, Pinia, Vitest, Lucide.

**Git note:** Do not stage, commit, push, rebase, or run production migrations while executing this plan; those actions require separate user authorization.

---

## File Structure

- `server/src/main/resources/db/migration/V5__template_tag_auto_increment.sql` enables generated tag IDs while recreating the unchanged tag-relation foreign key required by MySQL.
- `server/src/main/java/com/example/lowcode/template/infrastructure/TemplateTagMapper.java` owns SQL for tag writes and relation existence checks.
- `server/src/main/java/com/example/lowcode/template/application/TemplateTagAdminService.java` enforces roles, validation, strict delete semantics, and audits.
- `server/src/main/java/com/example/lowcode/template/api/AdminTemplateTagController.java` maps typed admin requests to service commands.
- `server/src/main/java/com/example/lowcode/common/exception/ErrorCode.java` adds the typed `409` conflict code.
- `server/src/main/java/com/example/lowcode/auth/security/SecurityConfig.java` admits DELETE CORS preflight requests.
- `server/src/test/java/com/example/lowcode/template/application/TemplateTagAdminServiceTest.java` proves write behavior without Docker.
- `server/src/test/java/com/example/lowcode/integration/DatabaseMigrationIT.java` verifies the generated-ID schema in Docker-capable environments.
- `poster-client/src/api/types.ts` declares create/update payloads.
- `poster-client/src/api/admin.ts` owns typed POST/PATCH/DELETE helpers.
- `poster-client/src/api/__tests__/admin.spec.ts` checks the browser request contracts.
- `poster-client/src/features/admin/AdminTemplateTagsPage.vue` adds administrator-only creation, edit, and delete dialogs.
- `poster-client/src/features/admin/__tests__/AdminTemplateTagsPage.spec.ts` proves admin write flows, conflict feedback, and operator read-only behavior.
- `docs/progress/2026-08-19-p1-template-tag-crud.md` records delivery evidence and remaining P1 gaps.
- `README.md` updates the P1 feature inventory.

### Task 1: Define Migration and Conflict Contract

**Files:**
- Create: `server/src/main/resources/db/migration/V5__template_tag_auto_increment.sql`
- Modify: `server/src/main/java/com/example/lowcode/common/exception/ErrorCode.java`
- Modify: `server/src/main/java/com/example/lowcode/auth/security/SecurityConfig.java`
- Modify: `server/src/test/java/com/example/lowcode/integration/DatabaseMigrationIT.java`

- [x] **Step 1: Add the initially failing migration assertion**

Add this test method to `DatabaseMigrationIT` before adding V5:

```java
@Test
void v5MakesTemplateTagIdAutoIncrement() {
    String extra = jdbcTemplate.queryForObject(
        """
            SELECT extra
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'template_tag'
              AND column_name = 'id'
            """,
        String.class
    );

    assertThat(extra).contains("auto_increment");
}
```

- [x] **Step 2: Run the migration integration test to establish the baseline**

Run: `$env:JAVA_HOME='C:\Program Files\Java\latest\jdk-21'; .\mvnw.cmd -Dtest=DatabaseMigrationIT test`

Expected: the test either fails because `id` lacks `auto_increment`, or Testcontainers reports that the Docker daemon is unavailable. Record the latter as an environment limitation; do not bypass Flyway.

- [x] **Step 3: Add the non-destructive Flyway migration and typed conflict**

Create `V5__template_tag_auto_increment.sql`:

```sql
ALTER TABLE template_tag_relation
    DROP FOREIGN KEY fk_template_tag_relation_tag;

ALTER TABLE template_tag
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;

ALTER TABLE template_tag_relation
    ADD CONSTRAINT fk_template_tag_relation_tag FOREIGN KEY (tag_id) REFERENCES template_tag (id);
```

Add the enum member below `NOT_FOUND` in `ErrorCode`:

```java
TEMPLATE_TAG_IN_USE(HttpStatus.CONFLICT, "标签仍被模板引用，无法删除"),
```

Extend CORS methods in `SecurityConfig`:

```java
configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
```

MySQL requires temporarily dropping the foreign key before changing its referenced column. The final migration restores `fk_template_tag_relation_tag` with the same `tag_id -> template_tag(id)` contract; it does not alter `template_tag_relation` rows, its primary key or index, or any public template-search query.

- [x] **Step 4: Re-run the migration check when Docker is available and compile locally**

Run: `$env:JAVA_HOME='C:\Program Files\Java\latest\jdk-21'; .\mvnw.cmd -DskipTests compile`

Expected: `BUILD SUCCESS`. When Docker is available, rerun `-Dtest=DatabaseMigrationIT test` and expect the V5 assertion to pass.

### Task 2: Write Failing Service Tests for Tag CRUD

**Files:**
- Modify: `server/src/test/java/com/example/lowcode/template/application/TemplateTagAdminServiceTest.java`

- [x] **Step 1: Add administrator create and update tests**

Add a create test that stubs `findByCode("holiday-sale")` to `null`, assigns generated ID `21L` in `mapper.insert`, and asserts the returned row/audit data:

```java
TemplateTagAdminService.TagView created = service.create(
    new CurrentUser(7, 11), "holiday-sale", " 节日促销 ", 20
);

assertThat(created).isEqualTo(new TemplateTagAdminService.TagView(
    21L, "holiday-sale", "节日促销", 20, "DRAFT"
));
assertThat(auditMapper.row.action()).isEqualTo("TEMPLATE_TAG_CREATE");
```

Add an update test that stubs `findByCode("promotion")`, `updateDetails("promotion", "活动促销", 30)`, and asserts the returned values plus `TEMPLATE_TAG_UPDATE` metadata containing old and new name/sort order.

- [x] **Step 2: Add strict delete and authorization tests**

Add tests for an unreferenced deletion, an in-use deletion, invalid inputs, duplicate code, and operator write rejection:

```java
when(mapper.findByCode("promotion")).thenReturn(tag("promotion", "DRAFT"));
when(mapper.countTemplateRelations(20L)).thenReturn(1);

assertThatThrownBy(() -> service.delete(new CurrentUser(7, 11), "promotion"))
    .isInstanceOf(BusinessException.class)
    .extracting(error -> ((BusinessException) error).errorCode())
    .isEqualTo(ErrorCode.TEMPLATE_TAG_IN_USE);
verify(mapper, never()).deleteById(20L);
```

For an unreferenced tag, stub count `0` and `deleteById(20L)` to return `1`; assert `TEMPLATE_TAG_DELETE` and the deleted ID. For `OPERATOR`, assert create, update, and delete all reject before mapper writes.

- [x] **Step 3: Run focused tests and confirm compilation is red**

Run: `$env:JAVA_HOME='C:\Program Files\Java\latest\jdk-21'; .\mvnw.cmd -Dtest=TemplateTagAdminServiceTest test`

Expected: compilation failure for missing `create`, `update`, `delete`, mapper methods, and/or `TEMPLATE_TAG_IN_USE` until Tasks 1 and 3 are complete.

### Task 3: Implement Mapper, Service, and Controller Commands

**Files:**
- Modify: `server/src/main/java/com/example/lowcode/template/infrastructure/TemplateTagMapper.java`
- Modify: `server/src/main/java/com/example/lowcode/template/application/TemplateTagAdminService.java`
- Modify: `server/src/main/java/com/example/lowcode/template/api/AdminTemplateTagController.java`
- Modify: `server/src/test/java/com/example/lowcode/template/application/TemplateTagAdminServiceTest.java`

- [x] **Step 1: Add generated-ID insert, update, relation count, and delete mapper methods**

Import `Delete`, `Insert`, and `Options`, then add these methods to `TemplateTagMapper`:

```java
@Insert("""
    INSERT INTO template_tag (code, name, sort_order, status)
    VALUES (#{code}, #{name}, #{sortOrder}, #{status})
    """)
@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
int insert(AdminTagRow row);

@Update("""
    UPDATE template_tag
    SET name = #{name}, sort_order = #{sortOrder}
    WHERE code = #{code}
    """)
int updateDetails(
    @Param("code") String code,
    @Param("name") String name,
    @Param("sortOrder") int sortOrder
);

@Select("SELECT COUNT(*) FROM template_tag_relation WHERE tag_id = #{tagId}")
int countTemplateRelations(@Param("tagId") long tagId);

@Delete("DELETE FROM template_tag WHERE id = #{tagId}")
int deleteById(@Param("tagId") long tagId);
```

The service checks references before calling `deleteById`; the existing FK remains a second database-level safeguard.

- [x] **Step 2: Add normalized create, update, and delete service commands**

Add the following public methods to `TemplateTagAdminService`:

```java
@Transactional
public TagView create(CurrentUser currentUser, String code, String name, Integer sortOrder) {
    AuthRepository.UserIdentity actor = requireAdministrator(currentUser);
    String normalizedCode = normalizeCode(code);
    String normalizedName = normalizeName(name);
    int normalizedSortOrder = normalizeSortOrder(sortOrder);
    if (tagMapper.findByCode(normalizedCode) != null) {
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模板标签编码已存在");
    }

    TemplateTagMapper.AdminTagRow tag = new TemplateTagMapper.AdminTagRow();
    tag.setCode(normalizedCode);
    tag.setName(normalizedName);
    tag.setSortOrder(normalizedSortOrder);
    tag.setStatus("DRAFT");
    if (tagMapper.insert(tag) != 1 || tag.getId() <= 0) {
        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "模板标签创建失败");
    }
    auditLogService.record(new AuditLogService.AuditEvent(
        actor.userId(), currentUser.tenantId(), "TEMPLATE_TAG_CREATE", "TEMPLATE_TAG",
        Long.toString(tag.getId()), AuditLogService.Outcome.SUCCESS, null,
        Map.of("code", tag.getCode(), "name", tag.getName(), "sortOrder", tag.getSortOrder(), "status", tag.getStatus())
    ));
    return toView(tag);
}

@Transactional
public TagView update(CurrentUser currentUser, String code, String name, Integer sortOrder) {
    AuthRepository.UserIdentity actor = requireAdministrator(currentUser);
    String normalizedCode = normalizeCode(code);
    String normalizedName = normalizeName(name);
    int normalizedSortOrder = normalizeSortOrder(sortOrder);
    TemplateTagMapper.AdminTagRow tag = tagMapper.findByCode(normalizedCode);
    if (tag == null) {
        throw new BusinessException(ErrorCode.NOT_FOUND, "标签不存在");
    }
    if (normalizedName.equals(tag.getName()) && normalizedSortOrder == tag.getSortOrder()) {
        return toView(tag);
    }
    if (tagMapper.updateDetails(normalizedCode, normalizedName, normalizedSortOrder) != 1) {
        throw new BusinessException(ErrorCode.NOT_FOUND, "标签不存在");
    }
    auditLogService.record(new AuditLogService.AuditEvent(
        actor.userId(), currentUser.tenantId(), "TEMPLATE_TAG_UPDATE", "TEMPLATE_TAG",
        Long.toString(tag.getId()), AuditLogService.Outcome.SUCCESS, null,
        Map.of("code", normalizedCode, "fromName", tag.getName(), "toName", normalizedName,
            "fromSortOrder", tag.getSortOrder(), "toSortOrder", normalizedSortOrder)
    ));
    tag.setName(normalizedName);
    tag.setSortOrder(normalizedSortOrder);
    return toView(tag);
}

@Transactional
public void delete(CurrentUser currentUser, String code) {
    AuthRepository.UserIdentity actor = requireAdministrator(currentUser);
    String normalizedCode = normalizeCode(code);
    TemplateTagMapper.AdminTagRow tag = tagMapper.findByCode(normalizedCode);
    if (tag == null) {
        throw new BusinessException(ErrorCode.NOT_FOUND, "标签不存在");
    }
    if (tagMapper.countTemplateRelations(tag.getId()) > 0) {
        throw new BusinessException(ErrorCode.TEMPLATE_TAG_IN_USE);
    }
    if (tagMapper.deleteById(tag.getId()) != 1) {
        throw new BusinessException(ErrorCode.NOT_FOUND, "标签不存在");
    }
    auditLogService.record(new AuditLogService.AuditEvent(
        actor.userId(), currentUser.tenantId(), "TEMPLATE_TAG_DELETE", "TEMPLATE_TAG",
        Long.toString(tag.getId()), AuditLogService.Outcome.SUCCESS, null,
        Map.of("code", tag.getCode(), "name", tag.getName(), "sortOrder", tag.getSortOrder(), "status", tag.getStatus())
    ));
}
```

Implement them with this exact operation order:

```java
AuthRepository.UserIdentity actor = requireAdministrator(currentUser);
String normalizedCode = normalizeCode(code);
String normalizedName = normalizeName(name);
int normalizedSortOrder = normalizeSortOrder(sortOrder);
```

`create` must reject non-null `findByCode(normalizedCode)`, create an `AdminTagRow` with status `DRAFT`, call `insert`, require a positive generated ID, write `TEMPLATE_TAG_CREATE`, and return `toView(row)`.

`update` must load the tag or return `NOT_FOUND`; when name and sort order are unchanged, return its view without SQL update or audit. Otherwise call `updateDetails`, write `TEMPLATE_TAG_UPDATE` with `fromName`, `toName`, `fromSortOrder`, and `toSortOrder`, mutate the in-memory row, and return it.

`delete` must load the tag or return `NOT_FOUND`; when `countTemplateRelations(tag.id()) > 0`, throw `new BusinessException(ErrorCode.TEMPLATE_TAG_IN_USE)`. Then require `deleteById(tag.id()) == 1`, write `TEMPLATE_TAG_DELETE` with `code`, `name`, `sortOrder`, and `status`, and return `void`.

Add normalization helpers:

```java
private String normalizeName(String value) {
    if (value == null) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模板标签名称无效");
    String normalized = value.trim();
    if (normalized.isEmpty() || normalized.length() > 128) {
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模板标签名称无效");
    }
    return normalized;
}

private int normalizeSortOrder(Integer value) {
    if (value == null || value < 0 || value > 100_000) {
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模板标签排序无效");
    }
    return value;
}
```

Change the shared administrator rejection text to `仅管理员可管理模板标签`, so it remains correct for status, create, update, and delete requests.

- [x] **Step 3: Expose REST endpoints in the existing controller**

Add `PostMapping` and `DeleteMapping` imports, then add controller methods and request records:

```java
@PostMapping
public ApiResponse<TemplateTagAdminService.TagView> create(
    @RequestBody CreateTagRequest body,
    @AuthenticationPrincipal Jwt jwt,
    HttpServletRequest request
) {
    return ApiResponse.success(
        tagService.create(CurrentUser.fromJwt(jwt), body.code(), body.name(), body.sortOrder()),
        (String) request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE)
    );
}

@PatchMapping("/{code}")
public ApiResponse<TemplateTagAdminService.TagView> update(
    @PathVariable String code,
    @RequestBody UpdateTagRequest body,
    @AuthenticationPrincipal Jwt jwt,
    HttpServletRequest request
) {
    return ApiResponse.success(
        tagService.update(CurrentUser.fromJwt(jwt), code, body.name(), body.sortOrder()),
        (String) request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE)
    );
}

@DeleteMapping("/{code}")
public ApiResponse<Void> delete(
    @PathVariable String code,
    @AuthenticationPrincipal Jwt jwt,
    HttpServletRequest request
) {
    tagService.delete(CurrentUser.fromJwt(jwt), code);
    return ApiResponse.success(null, (String) request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE));
}

public record CreateTagRequest(String code, String name, Integer sortOrder) {}
public record UpdateTagRequest(String name, Integer sortOrder) {}
```

- [x] **Step 4: Make service tests green and run related backend regressions**

Run: `$env:JAVA_HOME='C:\Program Files\Java\latest\jdk-21'; .\mvnw.cmd '-Dtest=TemplateTagAdminServiceTest,TemplateAdminServiceTest,TemplateCategoryAdminServiceTest' test`

Expected: all focused tests pass, including the newly added create/update/delete cases.

### Task 4: Add Typed Frontend API Contracts

**Files:**
- Modify: `poster-client/src/api/types.ts`
- Modify: `poster-client/src/api/admin.ts`
- Modify: `poster-client/src/api/__tests__/admin.spec.ts`

- [x] **Step 1: Add failing request-contract tests**

Extend the admin API test imports with `createAdminTemplateTag`, `updateAdminTemplateTag`, and `deleteAdminTemplateTag`. Add a test that queues three successful JSON envelopes and expects these exact calls:

```ts
await createAdminTemplateTag({ code: 'holiday-sale', name: '节日促销', sortOrder: 20 }, 'token')
await updateAdminTemplateTag('holiday-sale', { name: '节日活动', sortOrder: 30 }, 'token')
await deleteAdminTemplateTag('holiday-sale', 'token')

expect(fetchMock).toHaveBeenNthCalledWith(
  1,
  '/api/v1/admin/template-tags',
  expect.objectContaining({ method: 'POST', body: JSON.stringify({ code: 'holiday-sale', name: '节日促销', sortOrder: 20 }) }),
)
expect(fetchMock).toHaveBeenNthCalledWith(
  2,
  '/api/v1/admin/template-tags/holiday-sale',
  expect.objectContaining({ method: 'PATCH', body: JSON.stringify({ name: '节日活动', sortOrder: 30 }) }),
)
expect(fetchMock).toHaveBeenNthCalledWith(
  3,
  '/api/v1/admin/template-tags/holiday-sale',
  expect.objectContaining({ method: 'DELETE' }),
)
```

- [x] **Step 2: Run the focused API test and confirm it is red**

Run: `npm run test:unit -- src/api/__tests__/admin.spec.ts`

Expected: TypeScript/Vitest error because the three tag CRUD functions and payload types do not exist.

- [x] **Step 3: Add request payload types and API helpers**

Add to `types.ts`:

```ts
export interface CreateAdminTemplateTagInput {
  code: string
  name: string
  sortOrder: number
}

export interface UpdateAdminTemplateTagInput {
  name: string
  sortOrder: number
}
```

Import the types in `admin.ts` and add:

```ts
export function createAdminTemplateTag(input: CreateAdminTemplateTagInput, accessToken?: string | null) {
  return request<AdminTemplateTag>(
    '/api/v1/admin/template-tags',
    { method: 'POST', body: JSON.stringify(input) },
    accessToken,
  )
}

export function updateAdminTemplateTag(code: string, input: UpdateAdminTemplateTagInput, accessToken?: string | null) {
  return request<AdminTemplateTag>(
    `/api/v1/admin/template-tags/${encodeURIComponent(code)}`,
    { method: 'PATCH', body: JSON.stringify(input) },
    accessToken,
  )
}

export function deleteAdminTemplateTag(code: string, accessToken?: string | null) {
  return request<null>(`/api/v1/admin/template-tags/${encodeURIComponent(code)}`, { method: 'DELETE' }, accessToken)
}
```

- [x] **Step 4: Run API tests green**

Run: `npm run test:unit -- src/api/__tests__/admin.spec.ts`

Expected: all admin API tests pass.

### Task 5: Add Tag CRUD Dialogs and Page Tests

**Files:**
- Modify: `poster-client/src/features/admin/AdminTemplateTagsPage.vue`
- Modify: `poster-client/src/features/admin/__tests__/AdminTemplateTagsPage.spec.ts`

- [x] **Step 1: Extend page tests before implementation**

Extend the `adminApi` mock with all three new helpers. Add these focused cases:

```ts
import { ApiError } from '@/api/http'

it('creates and edits a tag for an administrator', async () => {
  // Click "新增标签", submit holiday-sale / 节日促销 / 20, then edit the new row.
  await waitFor(() => expect(adminApi.createAdminTemplateTag).toHaveBeenCalledWith(
    { code: 'holiday-sale', name: '节日促销', sortOrder: 20 }, 'access-token',
  ))
  await waitFor(() => expect(adminApi.updateAdminTemplateTag).toHaveBeenCalledWith(
    'holiday-sale', { name: '节日活动', sortOrder: 30 }, 'access-token',
  ))
})

it('removes an unreferenced tag and keeps an in-use tag after conflict', async () => {
  const promotion = { id: 20, code: 'promotion', name: '促销', sortOrder: 10, status: 'DRAFT' as const }
  const campaign = { id: 21, code: 'campaign', name: '活动', sortOrder: 20, status: 'PUBLISHED' as const }
  adminApi.loadAdminTemplateTags.mockResolvedValue([promotion, campaign])
  adminApi.deleteAdminTemplateTag
    .mockResolvedValueOnce(null)
    .mockRejectedValueOnce(new ApiError(409, 'TEMPLATE_TAG_IN_USE', '标签仍被模板引用，无法删除'))
  const session = useSessionStore()
  session.setSession({ accessToken: 'access-token', tokenType: 'Bearer', expiresIn: 900, userId: 7, tenantId: 11, tenantRole: 'ADMIN' }, '13800000000')
  render(AdminTemplateTagsPage, { global: { plugins: [pinia] } })

  await fireEvent.click(await screen.findByRole('button', { name: '删除 促销' }))
  await fireEvent.click(screen.getByRole('button', { name: '确认删除' }))
  await waitFor(() => expect(adminApi.deleteAdminTemplateTag).toHaveBeenCalledWith('promotion', 'access-token'))
  expect(screen.queryByText('促销')).not.toBeInTheDocument()

  await fireEvent.click(screen.getByRole('button', { name: '删除 活动' }))
  await fireEvent.click(screen.getByRole('button', { name: '确认删除' }))
  await waitFor(() => expect(adminApi.deleteAdminTemplateTag).toHaveBeenCalledWith('campaign', 'access-token'))
  expect(await screen.findByRole('alert')).toHaveTextContent('标签仍被模板引用')
  expect(screen.getByText('活动')).toBeVisible()
})

it('does not render tag write commands for an operator', async () => {
  expect(screen.queryByRole('button', { name: '新增标签' })).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: '编辑 促销' })).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: '删除 促销' })).not.toBeInTheDocument()
})
```

- [x] **Step 2: Run page tests and confirm the new cases are red**

Run: `npm run test:unit -- src/features/admin/__tests__/AdminTemplateTagsPage.spec.ts`

Expected: failures for missing CRUD API mocks, controls, dialogs, and state updates.

- [x] **Step 3: Add focused page state and command handlers**

Import `Plus`, `Pencil`, and `Trash2` plus the new API helpers. Add these typed state values and helper contract:

```ts
type TagForm = { mode: 'create' | 'edit'; code: string; name: string; sortOrder: string }

const form = ref<TagForm | null>(null)
const formSubmitting = ref(false)
const formError = ref<string | null>(null)
const pendingDelete = ref<AdminTemplateTag | null>(null)
const deleteSubmitting = ref(false)
const deleteError = ref<string | null>(null)
```

Implement `openCreate`, `openEdit(tag)`, `submitForm`, `openDelete(tag)`, and `confirmDelete` with these rules:

- `submitForm` parses `sortOrder` with `Number`; rejects non-integers before an API call, clears stale errors, disables its submit button, invokes create/update, and either appends/replaces the returned row.
- Creation preserves the server-returned `DRAFT`; editing does not expose code input as editable.
- `confirmDelete` invokes the API only after the user clicks `确认删除`; on success filters the row out, and on error leaves it in place.
- `TEMPLATE_TAG_IN_USE` shows `标签仍被模板引用，请先停用`; other errors show their API message.
- Operator state has no create, edit, or delete controls, while existing status selection remains disabled.

- [x] **Step 4: Render accessible create/edit and delete-confirmation dialogs**

Inside the page template, conditionally render a `role="dialog"` form for create/edit with:

```html
<input v-model="form.code" :readonly="form.mode === 'edit'" aria-label="标签编码" />
<input v-model="form.name" aria-label="标签名称" />
<input v-model="form.sortOrder" type="number" min="0" max="100000" step="1" aria-label="标签排序" />
<button type="submit" :disabled="formSubmitting">保存</button>
<button type="button" :disabled="formSubmitting" @click="form = null">取消</button>
```

Render a separate `role="alertdialog"` confirmation for `pendingDelete`, with a visible tag name, `取消`, and `确认删除` buttons. Add icon-only row commands with `aria-label="编辑 ${tag.name}"` and `aria-label="删除 ${tag.name}"`, `title` tooltips, and disabled state while that row is being submitted.

Use small scoped styles for the overlay, form grid, action icon buttons, and responsive stacking; preserve the existing page colors, 4-6px control radii, and table layout. Do not create a new global component or introduce a UI dependency.

- [x] **Step 5: Run page tests green**

Run: `npm run test:unit -- src/features/admin/__tests__/AdminTemplateTagsPage.spec.ts`

Expected: all existing and newly added tag-page tests pass.

### Task 6: Full Verification and Delivery Record

**Files:**
- Create: `docs/progress/2026-08-19-p1-template-tag-crud.md`
- Modify: `README.md`

- [x] **Step 1: Run all relevant verification commands**

Run these commands in order:

```powershell
cd server
$env:JAVA_HOME='C:\Program Files\Java\latest\jdk-21'
.\mvnw.cmd '-Dtest=TemplateTagAdminServiceTest,TemplateAdminServiceTest,TemplateCategoryAdminServiceTest' test
.\mvnw.cmd -DskipTests package

cd ..\poster-client
npm run test:unit
npm run build

cd ..
git diff --check
```

Expected: focused backend tests, backend packaging, all frontend unit tests, and frontend production build pass. `git diff --check` has no whitespace errors. If Testcontainers lacks Docker, document that the V5 integration check was not runnable rather than treating it as a product failure.

- [x] **Step 2: Record the completed vertical slice**

Create the progress record with the exact commands and observed test counts. Include:

- Tag CRUD endpoints, immutable code, initial `DRAFT`, and `ADMIN`/`OPERATOR` permissions.
- Strict `409 TEMPLATE_TAG_IN_USE` behavior, no auto-unlink guarantee, and the existing `DISABLED` alternative.
- V5 migration scope and the fact that production migration execution remains outside this task.
- New audit actions and the Docker/Testcontainers limitation if present.
- Remaining P1 gaps: WeChat adapter, template CRUD/association editing, cover/material management.

Update the README P1 description to say template tag CRUD is available and remove it from the remaining-gap wording. Keep P2 editor, schema, canvas, image upload, and export outside the stated scope.

- [x] **Step 3: Review the final diff against the approved specification**

Run: `git diff --check` and `git diff --stat`

Confirm the diff contains no code that changes template-tag associations, public discovery filters, file uploads, WeChat login, or P2 editor features.
