# P1 模板运营最小闭环设计规格

**日期：** 2026-08-19
**状态：** 已确认范围，进入实现计划

## 1. 目标

在现有租户后台权限和模板发现查询之上，提供一个不依赖编辑器的模板运营最小闭环。运营人员可以查看所有模板的运营状态，管理员可以发布或停用模板；公开模板查询继续只返回 `PUBLISHED` 模板。

## 2. 范围

### 本次包含

- 管理端模板列表，支持按 `DRAFT`、`PUBLISHED`、`DISABLED` 筛选。
- 返回模板基础运营信息：名称、尺寸、分类编码、封面资源 ID、推荐排序、状态、发布时间和更新时间。
- `ADMIN` 和 `OPERATOR` 可以读取列表。
- 只有 `ADMIN` 可以修改模板状态。
- 发布状态变更写入租户审计日志，动作名为 `TEMPLATE_STATUS_CHANGE`。
- 管理端新增 `/admin/templates` 页面，包含加载、错误、空结果、403、重试和状态更新反馈。

### 本次不包含

- 模板创建、删除、复制或字段编辑。
- 设计稿 JSON、画布元素、模板字段和编辑器界面。
- 封面或素材上传、标签维护、专题运营和批量操作。
- 修改数据库结构；`design_template` 已具备所需字段。

## 3. 权限与状态规则

模板是当前数据库中的全局运营资源，不按租户隔离；租户角色只用于管理端授权。服务端是权限边界，前端只负责隐藏不可用控件。

| 操作 | ADMIN | OPERATOR | USER |
| --- | --- | --- | --- |
| 查看模板列表 | 允许 | 允许 | 拒绝 |
| 修改模板状态 | 允许 | 拒绝 | 拒绝 |

状态值固定为 `DRAFT`、`PUBLISHED`、`DISABLED`，允许任意状态间转换。重复设置当前状态视为幂等成功，不写数据库、不产生审计事件。状态变为 `PUBLISHED` 时将 `published_at` 更新为当前时间；变为其他状态时将其清空，使该字段表示最近一次有效发布时间。

## 4. API 契约

所有接口继续使用现有 `ApiResponse<T>` 包装和 `Trace-Id`。

```text
GET /api/v1/admin/templates?status=
PATCH /api/v1/admin/templates/{templateId}/status
{ "status": "PUBLISHED" }
```

列表行字段：

```text
id, name, width, height, categoryCode, coverAssetId,
featuredRank, status, publishedAt, updatedAt
```

接口不返回 `schema_json` 和模板字段内容。`templateId` 必须为正整数，未知模板返回 `404`，非法状态返回统一的 `422` 校验错误。非管理角色返回 `403`。

状态变更成功时记录：

```text
action: TEMPLATE_STATUS_CHANGE
resourceType: DESIGN_TEMPLATE
resourceId: template id
metadata: { fromStatus, toStatus }
```

## 5. 服务端实现

- 在 `DesignTemplateMapper` 增加管理列表查询和状态更新 SQL，使用 `CASE` 同步维护 `published_at`。
- 新增 `TemplateAdminService`，复用现有成员状态检查和 `AuditLogService`，集中完成角色、ID、状态校验。
- 新增 `AdminTemplateController`，路径为 `/api/v1/admin/templates`。
- 使用服务单元测试覆盖角色、状态校验、未知模板、幂等更新、发布时间规则和审计事件。

## 6. 前端实现

- 在 `api/types.ts` 增加模板管理行和状态类型。
- 在 `api/admin.ts` 增加列表与状态更新请求。
- 新增 `AdminTemplatesPage.vue`，沿用成员和分类管理页的后台壳、状态选择、骨架屏、错误重试和空状态模式。
- 路由增加 `/admin/templates`，管理壳和现有后台页面导航增加入口。
- `ADMIN` 显示可编辑状态选择，`OPERATOR` 保持禁用并仍可查看。

## 7. 验收与测试

### 服务端

- `ADMIN` 能读取并改变状态，写入一次成功审计。
- `OPERATOR` 能读取但改变状态返回 `403`，且不调用更新 SQL。
- `USER` 无法读取列表。
- 非法状态、非正 ID、未知模板返回预期业务错误。
- 重复状态更新不改变发布时间，不产生审计。
- 发布设置时间，停用/草稿清空发布时间。

### 前端

- 管理员可以筛选模板并提交状态变更。
- 运营角色看到只读状态控件。
- 加载失败、403、空列表和变更失败均有明确状态，重试可再次请求。

## 8. 风险与后续

本切片不提供模板内容编辑能力，因此不会改变现有 `DesignSchema` 或 P2 编辑器边界。后续标签、封面、素材和专题运营应分别设计，避免把多个资源管理模型塞进同一页面和接口。
