# P1 模板标签 CRUD 设计规格

**日期：** 2026-08-19
**状态：** 已实现并复核

## 目标与范围

在已存在的标签列表与状态运营基础上，补齐后台标签创建、名称与排序编辑以及严格删除。标签仍是平台运营资源：`ADMIN` 可写，`OPERATOR` 只读；所有请求都在服务端重新确认当前成员和租户为 `ACTIVE`。

本切片不编辑模板与标签的关联，也不进入封面、素材上传、微信登录或 P2 编辑器。现有公开模板发现接口继续只返回 `PUBLISHED` 标签。

## 数据与删除规则

新增 `V5__template_tag_auto_increment.sql`，将 `template_tag.id` 改为 `AUTO_INCREMENT`。MySQL 不允许直接修改被引用的列，因此迁移会先移除 `fk_template_tag_relation_tag`，修改 `template_tag.id`，再以相同名称、列定义和引用目标重建该外键。现有标签 ID、`template_tag_relation` 数据和最终外键契约保持不变；数据库会从已有最大 ID 后分配新 ID，不需要数据回填。该 DDL 只在本地/测试环境验证，生产执行仍由人工评审和发布流程控制。

`DELETE` 先检查标签是否存在于 `template_tag_relation`：

- 未关联任何模板：在同一事务内物理删除标签并写审计。
- 已关联至少一个模板：不删除、不修改关联，返回 `409 TEMPLATE_TAG_IN_USE`。
- 运营人员可先将关联标签改为 `DISABLED`，但本切片不自动解除模板关联。

标签 `code` 创建后不可修改，防止公开筛选 URL 和既有模板查询语义发生静默变化。

## API 契约

沿用现有 `ApiResponse` 成功信封和统一错误响应。

```text
POST /api/v1/admin/template-tags
{
  "code": "holiday-sale",
  "name": "节日促销",
  "sortOrder": 20
}

PATCH /api/v1/admin/template-tags/{code}
{
  "name": "节日活动",
  "sortOrder": 30
}

PATCH /api/v1/admin/template-tags/{code}/status
{ "status": "PUBLISHED" }

DELETE /api/v1/admin/template-tags/{code}
```

- 创建成功返回新标签，初始状态固定为 `DRAFT`。
- 编辑成功返回更新后的标签；状态继续通过既有专用端点变更。
- 删除成功返回 `{ "code": "OK", "data": null }`，以保持现有 API 信封约定。
- `code` 使用 `[a-z0-9-]{1,64}`；`name` 去除首尾空白后长度为 `1..128`；`sortOrder` 为 `0..100000` 的整数。
- 编码重复、无效请求返回 `400 VALIDATION_ERROR`；未知标签返回 `404 NOT_FOUND`；已关联标签返回 `409 TEMPLATE_TAG_IN_USE`；未登录、无效成员、普通用户和运营角色继续返回既有 `401/403`。

响应标签结构保持为 `id`、`code`、`name`、`sortOrder`、`status`。

## 服务与审计

`TemplateTagAdminService` 保持现有列表和状态转换契约，并新增 create、update 与 delete 命令。所有写操作在事务内完成，先鉴权、再校验输入和当前资源状态，最后持久化并记录成功审计：

- `TEMPLATE_TAG_CREATE`：记录 `code`、`name`、`sortOrder`、初始状态。
- `TEMPLATE_TAG_UPDATE`：记录更新前后的 `name` 和 `sortOrder`。
- `TEMPLATE_TAG_DELETE`：记录被删除标签的 `code`、`name`、`sortOrder`、状态。

审计资源类型均为 `TEMPLATE_TAG`，资源 ID 为标签数字 ID。审计只保存受既有审计服务净化的元数据，不含用户令牌、Cookie 或手机号。

## 前端交互

`/admin/template-tags` 复用现有列表、状态筛选、加载、错误、空态和 403 视图。

- `ADMIN` 在工具栏看到“新增标签”命令；创建表单包含编码、名称和排序，提交期间禁用重复提交，成功后把返回行加入当前列表。
- 行操作使用具备可访问名称和提示的编辑、删除图标按钮。编辑表单只展示名称与排序；编码只读显示。
- 删除需要明确确认；服务端返回 `TEMPLATE_TAG_IN_USE` 时，保留列表并显示“标签仍被模板引用，请先停用”的行内错误。
- `OPERATOR` 只保留筛选、刷新和列表查看，不渲染任何写命令。

## 验收与验证

- 后端服务单测覆盖管理员创建默认草稿、更新、删除未关联标签、关联删除冲突、唯一编码、输入校验、审计和 `OPERATOR` 拒绝写操作；既有状态操作回归通过。
- 前端 API 测试覆盖 `POST`、`PATCH`、`DELETE` 请求契约；页面测试覆盖管理员创建/编辑/删除、关联冲突提示以及运营只读。
- 更新迁移集成断言，完整容器集成测试在 Docker 可用环境执行；本地至少运行聚焦单测、全量前端单测、前端构建、后端打包和 `git diff --check`。
