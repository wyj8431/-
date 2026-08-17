# 小商家海报平台设计说明

## 1. 产品定位

平台面向行业分散的小商家，帮助不懂设计的用户在 3 分钟内选择模板、替换促销信息并下载一张合格的朋友圈海报。

首批试用用户为 3 至 5 个真实商家。第一阶段以完成率和完成时长验证价值，不以模板数量或功能数量作为成功标准。

## 2. MVP 用户流程

1. 商家使用手机号或微信登录。
2. 商家浏览并选择一张朋友圈促销模板。
3. 商家填写模板开放的商品名称、价格、日期和联系方式字段。
4. 商家上传并裁剪商品图片。
5. 商家预览并保存设计稿。
6. 商家下载 PNG 或 JPG 文件。

管理员使用基础画布编辑器制作模板，可以创建和调整文字、图片、矩形等元素，并把部分元素标记为商家可编辑字段。商家端不开放任意元素编辑，避免破坏布局和延长制作时间。

## 3. 范围

MVP 包含用户认证、租户与成员、模板管理、模板字段配置、素材上传、设计稿保存、历史版本、前端图片导出以及所有者/编辑者/查看者权限。

MVP 不包含多人实时协作、AI 生图、印刷下单、复杂动画、计费订阅、直接发布到微信和通用业务低代码能力。

## 4. 方案选择

采用“管理员自由制作模板、商家修改固定字段”的模板驱动方案。

没有选择商家自由画布优先方案，因为撤销重做、图层、字体兼容、自由变换和精确导出会扩大首版范围。没有选择 AI 优先方案，因为生成结果仍需要稳定的模板、素材和编辑基础设施支撑。

自由画布将在第二阶段开放给商家，AI 生成将在核心编辑和导出链路稳定后加入。

## 5. 总体架构

```text
Vue 3 + TypeScript + Fabric.js
                |
              Nginx
                |
Spring Boot 3.x 模块化单体
  |-- auth / tenant / permission
  |-- template / design / asset
  |-- export / ai
                |
MySQL 8 + Redis + MinIO + RabbitMQ
```

主后端使用 Java 21、Spring Security、JWT、MyBatis-Plus 和 Flyway。第一阶段保持模块化单体，业务边界稳定且出现独立扩缩容需求后才考虑拆分服务。

## 6. 数据设计

设计稿采用“元数据 + 不可变版本”的存储模型：

- `design_document` 保存名称、归属、当前版本和状态。
- `design_version` 保存每次成功提交的完整设计 JSON。
- `design_permission` 保存所有者、编辑者和查看者授权。
- `design_template` 保存模板元数据和基础布局。
- `template_field` 保存商家可编辑字段及校验规则。
- `asset` 保存对象存储键、媒体类型、哈希和尺寸。
- `export_task` 保存异步导出状态和结果文件位置。

所有租户数据必须携带 `tenant_id`。服务层不能只按业务主键查询和修改租户数据。

## 7. 设计文档格式

设计 JSON 包含 `schemaVersion`、画布、页面和元素。元素使用稳定的 `id`、`type`、`transform`、`props`、`visible`、`locked` 和 `zIndex` 字段。新增元素类型通过扩展 `type` 与 `props` 实现，不为每个画布元素创建数据库表。

管理员发布模板时，服务端校验模板字段是否指向存在的元素、字段键是否唯一、必填字段是否存在默认值或允许用户输入。

## 8. 保存与冲突处理

客户端在用户停止操作 500 至 1000 毫秒后自动保存，并提交 `baseVersion` 和完整 JSON。服务端在事务中锁定或条件更新文档：

1. 校验访问权限和 JSON 结构。
2. 比较 `baseVersion` 与 `current_version`。
3. 不一致时返回 HTTP 409，不写入任何版本。
4. 一致时插入新的 `design_version`。
5. 更新 `design_document.current_version`。

MVP 返回冲突并要求用户重新加载。多人操作合并和 CRDT 不在第一阶段范围内。

## 9. 素材与导出

客户端通过预签名 URL 直接上传图片到 MinIO。完成回调必须校验对象存在、大小限制、文件魔数和 MIME 类型，并计算或确认 SHA-256。数据库和 JSON 只保存对象键，不保存 Base64 文件内容。

MVP 使用浏览器端 Fabric.js 导出 PNG/JPG。生产阶段加入 RabbitMQ 和独立 Node.js Playwright 渲染 Worker，Java 后端负责创建任务、授权、状态查询和结果签名下载。

## 10. 安全与错误

- Access Token 使用短期 JWT，Refresh Token 可撤销并存储哈希。
- 每个需要资源 ID 的接口同时校验租户与资源权限。
- 上传只允许配置的图片格式和大小，SVG 默认拒绝或经过严格清洗。
- 模板 JSON 使用白名单 Schema 校验，禁止执行用户脚本。
- 统一错误响应包含稳定错误码、用户可读消息和 `traceId`。
- 版本冲突返回 409，参数错误返回 400，未认证返回 401，无权限返回 403，资源不存在返回 404。

## 11. 测试与验收

单元测试覆盖模板字段校验、权限规则、版本冲突和导出状态流转。集成测试覆盖认证、租户隔离、MySQL 事务和 Flyway 迁移。接口测试覆盖第一条端到端链路。

产品验收标准：至少 80% 的首批试用商家无需开发人员代操作，能在 3 分钟内完成模板选择、内容填写、图片上传和海报下载。

## 12. 相关文档

- `docs/MVP_REQUIREMENTS.md`：产品需求、页面和验收场景。
- `docs/BACKEND_DESIGN.md`：Java 模块、数据库、API 和部署设计。
- `docs/REFERENCE_ARCHITECTURE_NOTES.md`：外部参考资料的采用、调整和延期决策。
- `docs/PRODUCT_REQUIREMENTS_ROADMAP.md`：补充工单与截图需求的分期、冲突和前置决策。

## 13. 编辑器内核原则

参考通用低代码编辑器的成熟做法，前端内核遵守以下原则，但不把通用网页低代码的全部范围带入海报 MVP：

1. **Schema First**：`DesignSchema`、`PageSchema` 和 `ElementSchema` 是编辑、预览、保存和导出的共同事实来源。
2. **API First**：前后端以版本化 REST 契约和 OpenAPI 文档协作，不以数据库实体直接作为接口模型。
3. **Type First**：TypeScript 类型、Java DTO 和 JSON Schema 的字段名称与含义保持一致。
4. **Registry First**：元素类型由 `ElementRegistry` 注册，注册项提供默认 Schema、编辑能力、属性面板定义和渲染器。
5. **Renderer 与 Editor 解耦**：编辑器只修改 Schema；编辑态、预览态和导出态共享同一渲染语义。
6. **状态与视图解耦**：Pinia 保存编辑会话状态，Vue 组件不各自持有页面结构副本。
7. **历史命令化**：添加、删除、移动、缩放和属性更新通过 Command/History 记录，以支持 Undo/Redo。

MVP 的元素注册表只包含 `text`、`image`、`rect` 和 `icon`。`button`、`form`、`table`、`chart`、数据源和事件动作属于通用业务低代码，使用独立 Schema 命名空间在后续阶段实现。

## 14. 参考架构适配

外部资料使用 React、Zustand、dnd-kit、Node.js、Prisma 和 PostgreSQL 描述通用网页低代码平台。本项目只采用其中的边界思想：

- React 组件结构映射为 Vue 3 Composition API 组件和 composable。
- Zustand 映射为 Pinia。
- 通用 DOM 拖拽映射为 Fabric.js 画布对象交互；组件库拖入画布时才使用 HTML5 Drag and Drop。
- Node.js 后端、Prisma 和 PostgreSQL 不采用，后端保持 Java 21、Spring Boot、MyBatis-Plus 和 MySQL 8。
- `PageSchema` 不直接等同于可发布网页；在海报领域中它表示一张画布页面。
- 发布动作在 MVP 中指模板发布和不可变设计版本，不提供公开网站发布。
- AI 只能生成或修改 Schema/Patch，禁止生成并执行前端源代码；该能力不进入 MVP。
