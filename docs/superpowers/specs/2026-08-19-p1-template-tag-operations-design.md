# P1 模板标签运营设计规格

**日期：** 2026-08-19
**状态：** 已确认范围，进入实现

## 目标与范围

为现有 `template_tag` 表补齐后台只读/状态运营闭环。`ADMIN` 和 `OPERATOR` 可按状态查看标签；只有 `ADMIN` 可将标签置为 `DRAFT`、`PUBLISHED` 或 `DISABLED`。成功状态变化记录 `TEMPLATE_TAG_STATUS_CHANGE` 审计事件。

本切片不包含标签创建、删除、改名、排序、模板标签关联编辑，也不修改数据库结构或公开模板查询契约。

## API

```text
GET /api/v1/admin/template-tags?status=
PATCH /api/v1/admin/template-tags/{code}/status
{ "status": "PUBLISHED" }
```

列表返回 `id`、`code`、`name`、`sortOrder`、`status`，不返回模板关联明细。编码使用 `[a-z0-9-]{1,64}` 校验；未知标签返回 `404`，非法状态返回 `422`。重复设置当前状态幂等成功且不写审计。

## 权限与审计

服务端重新读取当前租户成员，要求成员和租户均为 `ACTIVE`。`ADMIN`、`OPERATOR` 可列表，只有 `ADMIN` 可修改。审计资源类型为 `TEMPLATE_TAG`，资源 ID 使用标签数字 ID，metadata 记录 `fromStatus` 与 `toStatus`。

## 前端

新增 `/admin/template-tags`，沿用模板分类页的后台状态模式：状态筛选、管理员编辑、运营只读、加载/错误/空/403/重试反馈。后台所有现有导航加入“模板标签”入口。

## 验收

- 服务单测覆盖列表权限、管理员变更、运营拒绝、非法状态、非法编码、未知标签、幂等无审计。
- 前端 API 测试覆盖查询参数和 PATCH 请求；页面测试覆盖管理员变更和运营只读。
- 全量前端单测、生产构建和后端打包通过；Docker 不可用时记录集成测试限制。
