# All+poster Workbench and Template Discovery Design

## 1. Decision

All+poster adopts a desktop workbench homepage inspired by the supplied reference screenshot's information hierarchy, not its branding or visual assets. This is an enhancement of the existing template centre, not a change to the MVP's template-driven product direction: a merchant still starts with a published template and may later change only that template's configured fields. The homepage is an authenticated or anonymous creative starting point where users search for a design intent, browse published templates, and select a template.

The frontend is fixed to Vue 3, TypeScript, Pinia, and Fabric.js. The backend remains the existing Java 21 Spring Boot modular monolith. React, Zustand, competitor logos, competitor copy, competitor template covers, and competitor image assets are not used.

This document specifies only the first delivery: **workbench home and template discovery**. The editor, workspace, team collaboration, member billing, AI generation, video generation, image processing, printing, and payment each require their own implementation plan.

## 2. User Outcomes

### Anonymous visitor

1. Opens `/` and sees the workbench home.
2. Searches published templates by keyword or chooses a category, tag, hotspot, or recommendation.
3. Opens a published template detail and preview.
4. Selects “use template”; the client records the template ID as the post-login return action and sends the visitor to login.

### Signed-in member

1. Performs the same discovery flow.
2. Selects “use template”.
3. Calls the existing `POST /api/v1/designs` endpoint with the published template ID and a default document name.
4. Shows an in-product confirmation that the design was created and returns to the template detail or discovery page. It does not navigate to an editor route in this delivery; the merchant template-filling screen owns that next transition in a separately approved delivery.

The UI must not route users to an unfinished tool. “Smart creation”, “image tools”, “video tools”, “AI poster”, “batch creation”, “printing”, and “blank canvas” appear only as disabled cards with an explicit “coming in a later phase” label until their corresponding delivery is complete.

## 3. Information Architecture

The desktop homepage follows the approved workbench structure:

```text
left rail
  Home                -> /
  My work             -> disabled until workspace delivery
  Team                -> disabled until team delivery
  Help                -> hidden unless a configured external help URL exists

top bar
  All+poster brand
  Creation tools      -> disabled until tool delivery
  Notifications       -> disabled until notification delivery
  Member benefits     -> disabled until billing delivery
  Account menu        -> login or session controls

hero
  mode tabs: Design templates (active), Smart creation, Image tools, Video tools
  keyword search
  trending query tags

discovery
  New design: focuses template discovery while blank canvas is unavailable
  Quick cards: hotspot calendar, featured scenes, image tools as disabled where unfinished
  Recommendation sections: featured templates, hotspot calendar, editorial scenes
```

Mobile behavior is responsive rather than a scaled desktop screen: the left rail becomes a bottom navigation, the top actions move into an overflow menu, mode tabs scroll horizontally, and template cards use a two-column grid.

## 4. Frontend Boundaries

Create a Vue 3 Vite client under `poster-client/` in the implementation plan. The first delivery contains only these feature boundaries:

| Area | Responsibility |
| --- | --- |
| `features/home` | Workbench layout, mode tabs, trending tags, hotspot calendar, recommendation sections, loading/empty/error states. |
| `features/template` | Category and tag filters, debounced keyword search, pagination, template cards, detail preview, use-template action. |
| `stores/session` | Current login state, post-login return target, tenant identity from JWT-backed profile data. |
| `stores/home` | Home payload cache and retryable loading state. |
| `stores/template-query` | URL-backed query, category, tags, current page, result list, pagination metadata. |
| `api` | Typed DTO clients generated or manually aligned to the OpenAPI contract; never expose database row types directly to components. |

The `editor` store and editor route are not created in this delivery. After a successful design creation request, the template feature owns only the confirmation state and does not navigate to an unfinished route.

## 5. Backend Boundaries

The existing `template` module continues to own templates and published-state filtering. Add a separate `home` module that composes public discovery data and never writes design content.

```text
home
  HomeController
  HomeQueryService
  HomeRepository
  HomeTopic / HomeTopicTemplate

template
  TemplateController
  TemplateQueryService
  TemplateRepository
  TemplateCategory / TemplateTag
```

`home` may call a public query interface exposed by `template`; it must not access `template` MyBatis mappers directly. `design` remains the sole owner of design-document creation and versioning.

Public endpoints:

```text
GET /api/v1/home
GET /api/v1/template-categories
GET /api/v1/templates?keyword=&categoryCode=&tagCode=&page=&pageSize=
GET /api/v1/templates/{id}
GET /api/v1/template-cover-assets/{id}/content
```

`GET /api/v1/home`, categories, template listing, template detail, and cover content return only published public content. Query parameters are validated, keyword matching uses parameterized SQL, and the server applies a bounded page size. `page` defaults to `1`; `pageSize` defaults to `24` and cannot exceed `48`; `keyword` is trimmed and must contain at most 64 characters. Category and tag codes must match `^[a-z0-9-]{1,64}$`; an unknown, draft, or disabled code is a `400` validation error. The existing `POST /api/v1/designs` remains authenticated and continues to reject unpublished templates.

## 6. Data Model

Use Flyway migrations; do not alter `V1__backend_vertical_slice.sql`.

```text
template_category
  id, code, name, parent_id, sort_order, status, created_at, updated_at

template_tag
  id, code, name, sort_order, status, created_at, updated_at

template_tag_relation
  template_id, tag_id

template_cover_asset
  id, object_key, mime_type, file_size, width, height, sha256,
  status, created_at, updated_at

home_topic
  id, code, title, subtitle, type, cover_asset_id,
  starts_at, ends_at, sort_order, status, created_at, updated_at

home_topic_template
  topic_id, template_id, sort_order

design_template additions
  category_id, featured_rank
```

`template_category.code`, `template_tag.code`, and `home_topic.code` are unique stable API identifiers. `template_tag_relation` has a unique `(template_id, tag_id)` key; both it and `home_topic_template` use foreign keys to their parent resources and unique relation keys. A template has zero or one category during the V2 migration and may have multiple tags; published uncategorized templates remain discoverable through keyword and homepage recommendations but are omitted when a category filter is selected. `template_cover_asset` is platform-owned public media, separate from the tenant-owned `asset` table; it only permits JPEG, PNG, and WebP files that have completed the same size, MIME, magic-byte, and hash validation as tenant uploads. `design_template.cover_asset_id` and `home_topic.cover_asset_id` reference this table and remain nullable so the frontend can render a safe schema-based thumbnail fallback.

`home_topic.type` is limited to `HOTSPOT_CALENDAR` and `EDITORIAL_SCENE`; each discovery table's `status` is limited to `DRAFT`, `PUBLISHED`, and `DISABLED`. A topic is public only when it is `PUBLISHED`, `starts_at` is null or not later than the request time, and `ends_at` is null or later than the request time. A topic only exposes published linked templates. `GET /api/v1/template-cover-assets/{id}/content` resolves only an asset referenced by a public template or currently public topic and returns a short-lived redirect or streams the content; it never exposes a tenant `asset` object key or MinIO credentials.

Template list order is fixed: `featured_rank` ascending with null ranks last, then `published_at` descending, then `id` descending. Category and tag lists are ordered by `sort_order` ascending and then `id` ascending. Homepage collections use their corresponding rank or relation `sort_order` ascending and then `id` ascending. These stable orders make pagination deterministic.

Platform cover assets are distinct from tenant uploads. User tenant assets are never published as homepage covers without an explicit future moderation workflow.

## 7. API Shapes

The successful response wrapper remains `{ code, message, data, traceId }`.

`GET /api/v1/home` returns:

```json
{
  "trendingTags": [{"code": "festival", "name": "节日海报"}],
  "featuredTemplates": [{"id": 1001, "name": "夏日促销", "coverAssetId": 2001, "coverUrl": "/api/v1/template-cover-assets/2001/content", "categoryCode": "marketing", "tags": ["promotion"]}],
  "hotspotCalendar": [{"code": "mid-autumn", "title": "中秋节", "startsAt": "2026-09-25T00:00:00+08:00", "templateCount": 12}],
  "editorialScenes": [{"code": "new-term", "title": "开学季", "subtitle": "新学期内容创作", "coverAssetId": 2002, "coverUrl": "/api/v1/template-cover-assets/2002/content"}]
}
```

`GET /api/v1/templates` returns a paged payload with `items`, `page`, `pageSize`, and `total`. Each item contains only template metadata required by a card: ID, name, dimensions, nullable cover asset ID and cover URL, category code, tag codes, and published time. Full `schema_json` is returned only by template detail and by design creation, not in list pages. A null cover URL makes the client render a schema-based thumbnail fallback rather than request arbitrary media.

## 8. Error Handling and Security

- Public discovery endpoints return `200` with an empty collection when there is no matching content; they do not leak draft or disabled records.
- Invalid paging, category, or tag inputs return the existing validation response (`400`).
- “Use template” without a valid session redirects to login; a stale or rejected token receives the established JSON `401` response.
- `POST /api/v1/designs` still uses the caller’s tenant from JWT and does not accept tenant ID from the client.
- Template keywords, names, topic titles, and tags render as plain text; the frontend never injects server content as HTML.
- Homepage analytics, when later added, records opaque template/category/topic identifiers only, not search terms containing personal data.

## 9. Test and Acceptance Criteria

Backend tests must prove:

1. Anonymous callers can read home data and published templates.
2. Draft and disabled templates, categories, tags, and topics never appear in public results.
3. Keyword, category, tag, and pagination filters produce deterministic results and reject invalid bounds.
4. A topic hides unpublished linked templates rather than returning an invalid card.
5. An authenticated user can create a design from a listed template; a visitor is denied by the existing design endpoint.

Frontend tests must prove:

1. Search updates the URL query and survives a refresh.
2. Loading, empty, and retry states retain the workbench layout without layout shift.
3. Disabled later-phase tools show an explicit unavailable label and do not navigate.
4. A visitor using a template is returned to the selected action after login.
5. A signed-in user creates one design after the server responds successfully and sees a confirmation without being routed to an unfinished editor.

Product acceptance is met when a desktop or mobile visitor can locate a relevant published template through search, category, tag, hotspot, or recommendation, and a signed-in user can initiate the existing design flow without encountering an unfinished route.

## 10. Explicit Deferrals

The following are not part of this implementation plan: Fabric editor UI, blank-canvas document creation, image cropping, image AI, video generation, batch generation, print ordering, payment, membership enforcement, share links, workspace list, team management, WebSocket collaboration, notification delivery, and administration screens. Their visible homepage cards remain disabled until their own specs and plans are approved.

## 11. Source and Ownership Rules

The approved screenshot and the referenced public websites guide information hierarchy and interaction patterns only. All+poster uses its own brand, copy, icons, illustrations, template covers, and licensed or self-created media. No competitor screenshots, image assets, template JSON, pricing, or marketing copy may be stored in the product repository or served to users.
