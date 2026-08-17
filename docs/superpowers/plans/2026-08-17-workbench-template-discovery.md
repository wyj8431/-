# All+poster Workbench Template Discovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** Deliver an original All+poster workbench where visitors discover published templates and signed-in merchants create a design without being sent to an unfinished editor.

**Architecture:** The Java 21 Spring Boot modular monolith stays authoritative. template owns category, tag, template search, and public cover access; new home composes its read-only payload through template application services, never template mappers. A Vue 3 client in poster-client/ owns responsive workbench UI and session/discovery state through Pinia.

**Tech Stack:** Java 21, Spring Boot 3.5.16, MyBatis-Plus, MySQL 8.4, Flyway, MinIO, JUnit 5, Testcontainers, Vue 3, TypeScript, Vite, Pinia, Vue Router, Vitest, Playwright, Lucide Vue.

---

## File Map

| Path | Responsibility |
| --- | --- |
| server/src/main/resources/db/migration/V2__workbench_template_discovery.sql | Add discovery metadata without changing V1. |
| server/src/main/java/com/example/lowcode/template/ | Search, category, cover service, controllers, and MyBatis mapping. |
| server/src/main/java/com/example/lowcode/home/ | Read-only homepage query and controller. |
| server/src/test/java/com/example/lowcode/template/ | Search, endpoint, visibility, and cover tests. |
| server/src/test/java/com/example/lowcode/home/ | Homepage composition tests. |
| poster-client/ | Vue client, unit tests, browser tests, and original project art. |
| README.md, docs/BACKEND_DESIGN.md, docs/MVP_REQUIREMENTS.md | Runbook and contract updates. |

## Fixed Contract

- GET /api/v1/templates returns {items,page,pageSize,total}; its only query parameters are keyword, categoryCode, tagCode, page, and pageSize.
- page defaults to 1; pageSize defaults to 24 and has a maximum of 48; fixed ordering is featured rank, published time, then ID.
- GET /api/v1/home, GET /api/v1/template-categories, GET /api/v1/templates/**, and GET /api/v1/template-cover-assets/{id}/content are public reads. POST /api/v1/designs remains authenticated.
- A platform cover is distinct from tenant asset data. A missing or failed cover has a schema-based client thumbnail fallback.
- No editor, workspace, team, AI, video, pricing, notifications, or blank canvas is routed in this delivery.

### Task 1: Add the V2 Migration

**Files:**
- Create: server/src/main/resources/db/migration/V2__workbench_template_discovery.sql
- Modify: server/src/test/java/com/example/lowcode/integration/DatabaseMigrationIT.java

- [ ] **Step 1: Write the failing Flyway contract test**

Extend EXPECTED_TABLES with template_category, template_tag, template_tag_relation, template_cover_asset, home_topic, and home_topic_template. Verify category/tag records for V1 template 1001, and required keys on design_template.

~~~java
@Test
void v2AddsDiscoveryDataWithoutChangingTheV1Template() {
    assertThat(indexNames("design_template"))
        .contains("idx_template_discovery", "idx_template_featured");
    assertThat(foreignKeyNames("design_template"))
        .contains("fk_template_category", "fk_template_cover_asset");
    assertThat(jdbcTemplate.queryForObject(
        "SELECT category_id FROM design_template WHERE id = 1001", Long.class
    )).isEqualTo(10L);
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM template_tag_relation WHERE template_id = 1001 AND tag_id = 20",
        Integer.class
    )).isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject(
        "SELECT cover_asset_id FROM design_template WHERE id = 1001", Long.class
    )).isNull();
}
~~~

- [ ] **Step 2: Confirm the test is red**

~~~powershell
cd server
.\mvnw.cmd -Dtest=DatabaseMigrationIT test
~~~

Expected: V2 tables or keys are absent.

- [ ] **Step 3: Implement exact additive DDL**

Create template_category(id, code unique, name, parent_id FK, sort_order, status, timestamps); template_tag(id, code unique, name, sort_order, status, timestamps); template_cover_asset(id, object_key unique, mime_type, file_size, width, height, sha256, status, timestamps); template_tag_relation(template_id, tag_id) with its compound primary/unique key and reverse tag index; home_topic(id, code unique, title, subtitle, type, cover_asset_id, starts_at, ends_at, sort_order, status, timestamps); and home_topic_template(topic_id, template_id, sort_order).

All status checks use DRAFT, PUBLISHED, DISABLED. home_topic.type is limited to HOTSPOT_CALENDAR and EDITORIAL_SCENE. template_cover_asset permits only image/jpeg, image/png, image/webp; 1 to 10485760 bytes; 1 to 20000 dimensions; and 64-character SHA-256 values.

~~~sql
ALTER TABLE design_template
    ADD COLUMN category_id BIGINT NULL AFTER id,
    ADD COLUMN featured_rank INT NULL AFTER cover_asset_id,
    ADD KEY idx_template_discovery (status, category_id, published_at, id),
    ADD KEY idx_template_featured (status, featured_rank, published_at, id),
    ADD CONSTRAINT fk_template_category FOREIGN KEY (category_id) REFERENCES template_category (id),
    ADD CONSTRAINT fk_template_cover_asset FOREIGN KEY (cover_asset_id) REFERENCES template_cover_asset (id);

CREATE TABLE template_tag_relation (
    template_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (template_id, tag_id),
    UNIQUE KEY uk_template_tag_relation (template_id, tag_id),
    KEY idx_template_tag_relation_tag (tag_id, template_id),
    CONSTRAINT fk_template_tag_relation_template FOREIGN KEY (template_id) REFERENCES design_template (id),
    CONSTRAINT fk_template_tag_relation_tag FOREIGN KEY (tag_id) REFERENCES template_tag (id)
);
~~~

Seed category 10/marketing, tag 20/promotion, active editorial topic summer-promotion, and its relation to template 1001. Update template 1001 to category 10 and featured rank 10. Seed no object key and copy no competitor asset.

- [ ] **Step 4: Verify and commit**

~~~powershell
.\mvnw.cmd -Dtest=DatabaseMigrationIT test
git add src/main/resources/db/migration/V2__workbench_template_discovery.sql src/test/java/com/example/lowcode/integration/DatabaseMigrationIT.java
git commit -m "feat: add template discovery metadata"
~~~

Expected: Flyway accepts V1/V2 with no checksum errors.

### Task 2: Implement Typed Template Search and Category APIs

**Files:**
- Create: server/src/main/java/com/example/lowcode/template/application/TemplateSearchCriteria.java
- Create: server/src/main/java/com/example/lowcode/template/application/TemplatePage.java
- Modify: server/src/main/java/com/example/lowcode/template/application/TemplateRepository.java
- Modify: server/src/main/java/com/example/lowcode/template/application/TemplateQueryService.java
- Modify: server/src/main/java/com/example/lowcode/template/infrastructure/DesignTemplateMapper.java
- Create: server/src/main/java/com/example/lowcode/template/infrastructure/TemplateCategoryMapper.java
- Create: server/src/main/java/com/example/lowcode/template/infrastructure/TemplateTagMapper.java
- Modify: server/src/main/java/com/example/lowcode/template/infrastructure/MyBatisTemplateRepository.java
- Modify: server/src/main/java/com/example/lowcode/template/api/TemplateController.java
- Modify: server/src/test/java/com/example/lowcode/template/application/TemplateQueryServiceTest.java
- Modify: server/src/test/java/com/example/lowcode/template/api/TemplateControllerTest.java

- [ ] **Step 1: Write failing application and HTTP tests**

~~~java
var page = service.searchPublished(
    new TemplateSearchCriteria(" 夏日 ", "marketing", "promotion", 1, 24)
);
assertThat(page.page()).isEqualTo(1);
assertThat(page.pageSize()).isEqualTo(24);
assertThat(page.total()).isEqualTo(1L);
assertThat(page.items()).singleElement().satisfies(template -> {
    assertThat(template.categoryCode()).isEqualTo("marketing");
    assertThat(template.tagCodes()).containsExactly("promotion");
    assertThat(template.schema()).isNull();
});

mockMvc.perform(get("/api/v1/templates").queryParam("pageSize", "49"))
    .andExpect(status().isBadRequest())
    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
~~~

Add cases for page zero, a malformed code, a disabled category/tag, unknown public code, and draft template/topic relations. All must be hidden or return the existing VALIDATION_ERROR response, never 500.

- [ ] **Step 2: Confirm red**

~~~powershell
.\mvnw.cmd -Dtest=TemplateQueryServiceTest,TemplateControllerTest test
~~~

Expected: current list is a bare array and cannot satisfy the new contract.

- [ ] **Step 3: Implement value types and service validation**

~~~java
public record TemplateSearchCriteria(
    String keyword, String categoryCode, String tagCode, int page, int pageSize
) {
    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_PAGE_SIZE = 24;
    public static final int MAX_PAGE_SIZE = 48;
}

public record TemplatePage<T>(List<T> items, int page, int pageSize, long total) {
}
~~~

TemplateQueryService trims keyword, validates code against ^[a-z0-9-]{1,64}$, bounds page/pageSize, validates requested category/tag visibility, and returns TemplatePage. TemplateSummary contains id, name, width, height, nullable coverAssetId/coverUrl/categoryCode, ordered tagCodes, publishedAt, and null schema. Detail retains validated schema JSON.

- [ ] **Step 4: Implement bound MyBatis SQL**

Use @SelectProvider and bound values only. Use EXISTS for tag filtering so one template with many tags appears once.

~~~sql
WHERE t.status = 'PUBLISHED'
  AND (#{keyword} IS NULL OR LOWER(t.name) LIKE CONCAT('%', LOWER(#{keyword}), '%'))
  AND (#{categoryCode} IS NULL OR EXISTS (
      SELECT 1 FROM template_category c
      WHERE c.id = t.category_id AND c.status = 'PUBLISHED' AND c.code = #{categoryCode}
  ))
  AND (#{tagCode} IS NULL OR EXISTS (
      SELECT 1 FROM template_tag_relation r
      JOIN template_tag g ON g.id = r.tag_id AND g.status = 'PUBLISHED'
      WHERE r.template_id = t.id AND g.code = #{tagCode}
  ))
ORDER BY t.featured_rank IS NULL ASC, t.featured_rank ASC, t.published_at DESC, t.id DESC
LIMIT #{limit} OFFSET #{offset}
~~~

Use the same predicates for count. Fetch categories/tags in batches; public cards omit disabled metadata. No list query parses schema_json.

- [ ] **Step 5: Implement controller contract and commit**

TemplateController binds keyword/categoryCode/tagCode plus defaults page=1/pageSize=24 and returns ApiResponse<TemplatePage<TemplateSummary>>. Add GET /api/v1/template-categories with code/name/parentCode only.

~~~powershell
.\mvnw.cmd -Dtest=TemplateQueryServiceTest,TemplateControllerTest test
git add src/main/java/com/example/lowcode/template src/test/java/com/example/lowcode/template
git commit -m "feat: search published templates"
~~~

### Task 3: Add Public Cover Delivery and Home Composition

**Files:**
- Create: server/src/main/java/com/example/lowcode/template/api/TemplateCoverController.java
- Create: server/src/main/java/com/example/lowcode/template/application/TemplateCoverRepository.java
- Create: server/src/main/java/com/example/lowcode/template/application/TemplateCoverService.java
- Create: server/src/main/java/com/example/lowcode/template/infrastructure/TemplateCoverAssetMapper.java
- Create: server/src/main/java/com/example/lowcode/template/infrastructure/MyBatisTemplateCoverRepository.java
- Create: server/src/main/java/com/example/lowcode/home/api/HomeController.java
- Create: server/src/main/java/com/example/lowcode/home/application/HomeRepository.java
- Create: server/src/main/java/com/example/lowcode/home/application/HomeQueryService.java
- Create: server/src/main/java/com/example/lowcode/home/infrastructure/HomeTopicMapper.java
- Create: server/src/main/java/com/example/lowcode/home/infrastructure/MyBatisHomeRepository.java
- Modify: server/src/main/java/com/example/lowcode/auth/security/SecurityConfig.java
- Modify: server/src/main/resources/application.yml
- Create: server/src/test/java/com/example/lowcode/template/application/TemplateCoverServiceTest.java
- Create: server/src/test/java/com/example/lowcode/home/application/HomeQueryServiceTest.java
- Create: server/src/test/java/com/example/lowcode/home/api/HomeControllerTest.java

- [ ] **Step 1: Write failing tests**

Use a fake StorageGateway. Published referenced cover bytes return their MIME type; draft, disabled, unreferenced, and tenant assets map to NOT_FOUND. Fix Clock to 2026-08-17T00:00:00Z: future/expired topics are absent; a calendar with zero visible templates stays with count zero; an empty editorial scene is omitted.

~~~java
assertThat(service.loadHome()).satisfies(home -> {
    assertThat(home.trendingTags()).extracting(HomeQueryService.TagView::code)
        .contains("promotion");
    assertThat(home.featuredTemplates()).extracting(TemplateQueryService.TemplateSummary::id)
        .contains(1001L);
    assertThat(home.editorialScenes()).extracting(HomeQueryService.TopicView::code)
        .contains("summer-promotion");
});
~~~

- [ ] **Step 2: Confirm red**

~~~powershell
.\mvnw.cmd -Dtest=TemplateCoverServiceTest,HomeQueryServiceTest,HomeControllerTest test
~~~

Expected: cover and home classes are absent.

- [ ] **Step 3: Implement public cover access**

TemplateCoverService queries metadata only when cover status is PUBLISHED and referenced by a published template or currently active public topic. It invokes StorageGateway.readBounded(objectKey, 10485760L). TemplateCoverController returns bytes with exact image Content-Type, Cache-Control public max-age=300, and X-Content-Type-Options nosniff. It returns no object key, presigned URL, or MinIO credential.

- [ ] **Step 4: Implement home ownership boundary**

HomeRepository returns ordered topic/tag/template IDs. HomeQueryService receives TemplateQueryService and transforms public template DTOs; it imports no template.infrastructure class and performs no writes.

~~~java
public record HomeView(
    List<TagView> trendingTags,
    List<TemplateQueryService.TemplateSummary> featuredTemplates,
    List<HotspotView> hotspotCalendar,
    List<TopicView> editorialScenes
) {
}
public record HotspotView(String code, String title, Instant startsAt, long templateCount) {
}
public record TopicView(String code, String title, String subtitle, Long coverAssetId, String coverUrl) {
}
~~~

HomeTopicMapper requires PUBLISHED, start <= clock when supplied, end > clock when supplied, and sort_order/id ordering. HomeController returns the existing ApiResponse wrapper.

- [ ] **Step 5: Configure security, run tests, commit**

Permit only GET /api/v1/home, GET /api/v1/template-categories, GET /api/v1/templates/**, and GET /api/v1/template-cover-assets/**. Add a configuration property named app.web.allowed-origins, sourced from WEB_ALLOWED_ORIGINS and defaulting to http://localhost:5173; allow GET, POST, PATCH, OPTIONS, Authorization, and Content-Type only.

~~~powershell
.\mvnw.cmd -Dtest=TemplateCoverServiceTest,HomeQueryServiceTest,HomeControllerTest test
git add src/main/java/com/example/lowcode/home src/main/java/com/example/lowcode/template src/main/java/com/example/lowcode/auth/security/SecurityConfig.java src/main/resources/application.yml src/test/java/com/example/lowcode/home src/test/java/com/example/lowcode/template
git commit -m "feat: expose workbench discovery APIs"
~~~

### Task 4: Scaffold Client, Typed State, and Original Assets

**Files:**
- Create: poster-client/package.json
- Create: poster-client/vite.config.ts
- Create: poster-client/tsconfig.json
- Create: poster-client/index.html
- Create: poster-client/src/main.ts
- Create: poster-client/src/App.vue
- Create: poster-client/src/styles/base.css
- Create: poster-client/src/router/index.ts
- Create: poster-client/src/api/http.ts
- Create: poster-client/src/api/auth.ts
- Create: poster-client/src/api/home.ts
- Create: poster-client/src/api/templates.ts
- Create: poster-client/src/api/designs.ts
- Create: poster-client/src/api/types.ts
- Create: poster-client/src/stores/session.ts
- Create: poster-client/src/stores/home.ts
- Create: poster-client/src/stores/template-query.ts
- Create: poster-client/src/stores/notice.ts
- Create: poster-client/src/assets/template-art/*.webp
- Create: poster-client/src/test/setup.ts
- Create: poster-client/vitest.config.ts
- Create: poster-client/playwright.config.ts
- Create: poster-client/src/stores/__tests__/template-query.spec.ts
- Create: poster-client/src/stores/__tests__/session.spec.ts

- [ ] **Step 1: Generate original local visual assets**

Invoke imagegen during execution. Create six original small template-art images: seasonal promotion, food offer, salon appointment, education opening, retail clearance, and community event. Each prompt prohibits logos, recognized competitor layouts, copied Chinese marketing text, and reference-site assets. Inspect images and retain only project-owned output.

- [ ] **Step 2: Write failing client state tests**

~~~ts
it('hydrates keyword and resets page through URL state', async () => {
  router.push('/?keyword=%E4%BF%83%E9%94%80&page=2')
  await router.isReady()
  query.setKeyword('餐饮')
  expect(router.currentRoute.value.query).toMatchObject({
    keyword: '餐饮',
    page: '1',
  })
})
~~~

Add tests that a 350ms debounced keyword request runs once, a newer query aborts the old request, and one successful login resumes exactly one pending design creation then clears it.

- [ ] **Step 3: Implement client plumbing**

Use Vue 3, Pinia, Vue Router, Lucide Vue, VueUse, Vite, TypeScript, Vitest, Testing Library, and Playwright. Scripts: dev, build using vue-tsc --noEmit and vite build, test:unit, test:e2e. Vite proxies /api to http://localhost:8080.

http.ts parses the existing envelope and throws this type without logging token values or raw responses:

~~~ts
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly traceId?: string,
  ) {
    super(message)
  }
}
~~~

Only unexpired session state attaches Authorization. Store accessToken, userId, tenantId, expiresAt, and at most one pending template action. Do not decode JWTs. template-query owns keyword/categoryCode/tagCode/page/page data, reads/writes route query, debounces 350ms, and keeps previous grid dimensions while loading.

- [ ] **Step 4: Build and commit**

~~~powershell
cd poster-client
npm install
npm run build
npm run test:unit
git add .
git commit -m "feat: scaffold all poster workbench client"
~~~

### Task 5: Build Responsive Workbench, Detail, Login Continuation, and Creation Confirmation

**Files:**
- Create: poster-client/src/features/auth/LoginDialog.vue
- Create: poster-client/src/features/home/WorkbenchShell.vue
- Create: poster-client/src/features/home/ModeTabs.vue
- Create: poster-client/src/features/home/TrendingTags.vue
- Create: poster-client/src/features/home/HomeSection.vue
- Create: poster-client/src/features/home/HomeTopicStrip.vue
- Create: poster-client/src/features/template/TemplateSearchBar.vue
- Create: poster-client/src/features/template/TemplateFilterBar.vue
- Create: poster-client/src/features/template/TemplateGrid.vue
- Create: poster-client/src/features/template/TemplateCard.vue
- Create: poster-client/src/features/template/SchemaThumbnail.vue
- Create: poster-client/src/features/template/TemplatePager.vue
- Create: poster-client/src/features/template/TemplateDetailDialog.vue
- Create: poster-client/src/features/template/DesignCreatedDialog.vue
- Create: poster-client/src/features/home/__tests__/WorkbenchShell.spec.ts
- Create: poster-client/src/features/template/__tests__/TemplateGrid.spec.ts
- Create: poster-client/src/features/template/__tests__/TemplateDetailDialog.spec.ts
- Modify: poster-client/src/App.vue
- Modify: poster-client/src/styles/base.css

- [ ] **Step 1: Write failing component tests**

~~~ts
it('keeps later-phase tools non-navigable', async () => {
  render(WorkbenchShell, { global: { plugins: [createPinia(), router] } })
  await userEvent.click(await screen.findByRole('button', { name: '智能创作' }))
  expect(router.currentRoute.value.path).toBe('/')
  expect(screen.getByText('将在后续阶段开放')).toBeVisible()
})

it('uses schema thumbnail without a cover URL', () => {
  render(TemplateCard, { props: { template: withoutCover } })
  expect(screen.getByLabelText('朋友圈促销模板预览')).toBeVisible()
  expect(screen.queryByRole('img')).not.toBeInTheDocument()
})
~~~

Add an interaction test: visitor chooses 使用此模板, completes login, createDesign runs once, success dialog appears, and browser remains at /.

- [ ] **Step 2: Confirm red**

~~~powershell
cd poster-client
npm run test:unit -- WorkbenchShell TemplateGrid TemplateDetailDialog
~~~

Expected: components are absent.

- [ ] **Step 3: Implement visual and responsive home**

Use this structure:

~~~text
left rail: All+poster, 首页 active, 我的 disabled, 团队 disabled, configured help
top bar: 模板中心, notifications disabled, member benefits disabled, account/login
main: mode tabs, search, trending tags, blank-canvas unavailable control
      featured templates, hotspot calendar, editorial scenes
      category filters, template grid, pagination
~~~

Only 设计模板 is active. Future tools use disabled buttons with visible 将在后续阶段开放 text. New design focuses discovery and announces blank canvas is unavailable. Cards use a fixed artboard aspect ratio, an image fallback on load failure, 8px maximum radius, skeletons without layout shift, retry/empty state, Lucide buttons/tooltips, and plain text only.

Under 768px, rail becomes fixed bottom navigation; actions move to overflow; mode tabs scroll; card grid has two columns; main content has bottom padding.

- [ ] **Step 4: Implement detail and creation flow**

Template detail retrieves GET /api/v1/templates/{id}. LoginDialog validates mainland-China phone and nonempty code. The primary action executes:

1. No valid session: store one pending action and open LoginDialog.
2. Valid session: disable button, POST /api/v1/designs, wait.
3. Success: clear pending action and show DesignCreatedDialog.
4. Failure: leave detail open, restore button, render plain-text retry error.

DesignCreatedDialog has only 继续浏览模板 and 关闭. It must not route to editor/workspace or promise a release date.

- [ ] **Step 5: Run checks and commit**

~~~powershell
cd poster-client
npm run test:unit
npm run build
git add src/features src/App.vue src/styles/base.css
git commit -m "feat: build responsive template workbench"
~~~

### Task 6: Browser Verification and Documentation

**Files:**
- Create: poster-client/e2e/workbench.spec.ts
- Modify: README.md
- Modify: docs/BACKEND_DESIGN.md
- Modify: docs/MVP_REQUIREMENTS.md

- [ ] **Step 1: Write failing desktop/mobile E2E test**

~~~ts
test('visitor discovers and a member creates without an unfinished route', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('searchbox', { name: '搜索模板' }).fill('促销')
  await expect(page.getByText('朋友圈促销')).toBeVisible()
  await page.getByText('朋友圈促销').click()
  await page.getByRole('button', { name: '使用此模板' }).click()
  await loginWithLocalCode(page, '13800138000', '123456')
  await expect(page.getByRole('dialog', { name: '设计稿已创建' })).toBeVisible()
  await expect(page).toHaveURL(/\/$/)
})
~~~

Use projects 1440x960 and 390x844. Mobile asserts visible bottom navigation and produces a screenshot for overlap inspection.

- [ ] **Step 2: Make E2E deterministic**

Playwright starts Vite on 5173 and reuses a pre-existing server only outside CI. It uses only seeded template 1001 and local code 123456; it does not write third-party data.

- [ ] **Step 3: Document and verify**

README gains Node 20+ prerequisite, client install/dev/test commands, client URL, and WEB_ALLOWED_ORIGINS local value. BACKEND_DESIGN.md documents home ownership, public discovery API, V2 schema, and cover security. MVP requirements adds workbench/filtering to template discovery without changing exclusions.

~~~powershell
cd server
.\mvnw.cmd test
.\mvnw.cmd -Dtest=DatabaseMigrationIT,HomeControllerTest,TemplateControllerTest test
.\mvnw.cmd clean verify

cd ..\poster-client
npm run build
npm run test:unit
npm run test:e2e
~~~

Inspect desktop/mobile screenshots: the scene must be nonblank, legible, and free from overlap. Then commit:

~~~powershell
cd ..
git add README.md docs/BACKEND_DESIGN.md docs/MVP_REQUIREMENTS.md poster-client/e2e
git commit -m "test: verify workbench template discovery"
~~~

## Requirement Coverage

| Requirement | Tasks |
| --- | --- |
| Search, categories, tags, pagination, stable order | 1, 2 |
| Homepage recommendations and active topics | 1, 3, 5 |
| Tenant asset isolation and public cover delivery | 1, 3 |
| Responsive Vue workbench and original art | 4, 5 |
| Login resume and one-time design creation | 4, 5, 6 |
| Backend, unit, desktop, and mobile verification | 1 through 6 |

## Plan Self-Review

- The plan covers each approved API, table, UI state, security rule, and deferral.
- It preserves V1 and uses the existing Flyway, MyBatis, response envelope, MinIO gateway, Testcontainers support, and design creation endpoint.
- V2 category remains nullable for migration.
- No step enters an unfinished editor, workspace, team, payment, AI, video, or blank-canvas route.
