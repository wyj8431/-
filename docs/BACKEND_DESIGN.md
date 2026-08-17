# Java 后端设计

## 技术栈

```text
Java 21
Spring Boot 3.x
Spring Security + JWT
MyBatis-Plus
MySQL 8
Redis
MinIO
RabbitMQ
Flyway
Docker Compose
JUnit 5 + Testcontainers
```

项目使用 Maven Wrapper。生产代码采用模块化单体，通过包边界隔离业务；不在 MVP 阶段引入 Spring Cloud。

## 包结构

```text
com.example.lowcode
|-- LowCodeApplication
|-- common
|   |-- api
|   |-- exception
|   |-- security
|   `-- validation
|-- auth
|-- tenant
|-- permission
|-- template
|-- design
|-- asset
|-- export
|-- ai
`-- infrastructure
```

业务模块内部优先按功能聚合 Controller、Application Service、Domain Model、Repository 接口和基础设施实现。业务模块不能直接依赖其他模块的数据库 Mapper，通过公开的应用服务或查询接口协作。

## 模块职责

| 模块 | 职责 |
| --- | --- |
| `auth` | 登录、Token 签发、刷新和撤销 |
| `tenant` | 商家租户、成员和成员状态 |
| `permission` | 设计稿 OWNER/EDITOR/VIEWER 权限判断 |
| `template` | 模板、分类、字段、发布校验和复制 |
| `design` | 设计元数据、版本、自动保存和回收站 |
| `asset` | 预签名上传、文件校验和素材元数据 |
| `export` | 导出任务创建、查询和状态流转 |
| `ai` | AI 任务协议预留，不实现具体模型能力 |

## 数据库模型

所有业务主键使用 `BIGINT`，由应用统一生成。所有业务表包含 `created_at` 和 `updated_at`，不可变版本表只包含 `created_at`。

### 身份和租户

- `sys_user(id, phone, wechat_open_id, nickname, status, created_at, updated_at)`
- `sys_tenant(id, name, status, owner_user_id, created_at, updated_at)`
- `sys_tenant_member(id, tenant_id, user_id, role, status, created_at, updated_at)`

约束：手机号和微信 Open ID 分别唯一；`(tenant_id, user_id)` 唯一。

### 模板

- `template_category(id, parent_id, name, sort_order, status)`
- `design_template(id, category_id, name, cover_asset_id, schema_json, status, created_by, published_at)`
- `template_field(id, template_id, element_id, field_key, label, field_type, required, default_value, validation_json)`

约束：`(template_id, field_key)` 唯一；发布查询索引为 `(status, category_id, published_at)`。

### 设计稿和权限

- `design_document(id, tenant_id, owner_id, template_id, name, width, height, current_version, status, created_at, updated_at)`
- `design_version(id, document_id, version_no, schema_json, created_by, created_at)`
- `design_permission(id, document_id, user_id, role, created_at, updated_at)`

约束：`(document_id, version_no)` 和 `(document_id, user_id)` 唯一。列表查询索引为 `(tenant_id, updated_at)` 和 `(owner_id, updated_at)`。

### 素材和任务

- `asset(id, tenant_id, owner_id, folder_id, object_key, file_name, mime_type, file_size, sha256, width, height, status, created_at)`
- `asset_folder(id, tenant_id, parent_id, name, created_at, updated_at)`
- `font_resource(id, name, family, object_key, license_type, status)`
- `export_task(id, tenant_id, document_id, version_id, format, status, output_object_key, error_message, created_at, started_at, finished_at)`
- `ai_task(id, tenant_id, user_id, task_type, status, request_json, result_json, error_message, created_at, finished_at)`
- `operation_log(id, tenant_id, user_id, action, resource_type, resource_id, detail_json, created_at)`

任务拉取索引为 `(status, created_at)`。对象键全局唯一，文件哈希按租户建立普通索引用于去重提示。

## 设计 JSON 契约

根节点必须包含：

```json
{
  "schemaVersion": 1,
  "canvas": {
    "width": 1080,
    "height": 1440,
    "background": "#ffffff"
  },
  "pages": []
}
```

服务端执行结构、大小和引用完整性校验。MVP 限制单个设计 JSON 不超过 2 MB、页面不超过 20 个、单页元素不超过 500 个。不允许脚本、HTML 事件处理器、`javascript:` URL 或任意外部资源 URL。

## API 约定

统一成功响应：

```json
{
  "code": "OK",
  "message": "success",
  "data": {},
  "traceId": "01K..."
}
```

统一错误响应：

```json
{
  "code": "DESIGN_VERSION_CONFLICT",
  "message": "设计稿已被更新，请重新加载",
  "traceId": "01K...",
  "details": {}
}
```

核心接口：

```text
POST   /api/v1/auth/register
POST   /api/v1/auth/login
POST   /api/v1/auth/refresh

GET    /api/v1/templates
GET    /api/v1/templates/{id}
POST   /api/v1/templates/{id}/clone

POST   /api/v1/designs
GET    /api/v1/designs
GET    /api/v1/designs/{id}
PATCH  /api/v1/designs/{id}
GET    /api/v1/designs/{id}/versions

POST   /api/v1/assets/presign
POST   /api/v1/assets/complete

POST   /api/v1/designs/{id}/exports
GET    /api/v1/exports/{taskId}
```

### 创建设计稿

```json
POST /api/v1/designs
{
  "templateId": 1001,
  "name": "周末促销"
}
```

服务端在同一事务中读取已发布模板、复制模板 JSON、创建设计元数据、写入版本 1 并授予创建者 OWNER 权限。

### 保存设计稿

```json
PATCH /api/v1/designs/2001
{
  "baseVersion": 12,
  "schema": {
    "schemaVersion": 1,
    "canvas": {},
    "pages": []
  }
}
```

服务端校验租户、EDITOR 或 OWNER 权限、JSON Schema 和版本号。版本一致时写入版本 13 并更新当前版本；不一致时返回 HTTP 409。

## 事务边界

- 注册：创建用户、默认租户和 OWNER 成员关系属于一个事务。
- 模板复制：读取模板后创建文档、初始版本和权限属于一个事务。
- 保存版本：插入版本和更新当前版本属于一个事务。
- 完成上传：校验对象并写素材元数据属于一个事务；对象删除失败通过补偿任务清理。
- 创建导出：读取版本、校验权限和创建任务属于一个事务；消息在事务提交后发送。

## 认证与权限

- Spring Security 解析 JWT 并创建只包含 `userId`、`tenantId` 和 Token ID 的身份上下文。
- Access Token 有效期建议 15 分钟，Refresh Token 有效期建议 30 天。
- Refresh Token 只保存哈希，可按设备撤销。
- 资源查询必须带上 `tenant_id`；权限服务再判断 OWNER、EDITOR 或 VIEWER。
- 平台管理员权限和商家租户权限分开，不能通过角色名称互相替代。

## 素材上传

1. 客户端请求预签名 URL，提交文件名、大小、预期 MIME 和 SHA-256。
2. 服务端校验限额并生成受租户前缀约束的对象键。
3. 客户端上传到 MinIO。
4. 客户端调用完成接口。
5. 服务端读取对象头和文件魔数，校验大小、类型与哈希后写入 `asset`。

对象键示例：`tenant/{tenantId}/asset/{yyyy}/{MM}/{id}.{ext}`。

## 导出

MVP 由前端 Fabric.js 导出。服务端异步导出启用后：

1. Java 服务创建 `PENDING` 任务并发送消息。
2. Worker 原子领取并更新为 `PROCESSING`。
3. Worker 使用固定版本 JSON 和受控素材 URL 渲染。
4. 成功后上传文件并更新为 `SUCCEEDED`。
5. 失败后记录安全化错误信息并更新为 `FAILED`。

同一文档、版本、格式和导出参数使用幂等键避免重复任务。

## 错误处理与可观测性

- 使用全局异常处理器映射业务错误码和 HTTP 状态。
- 每个请求生成或透传 `traceId`，日志使用结构化 JSON。
- 不在响应或日志中输出 Token、验证码、预签名 URL 和完整个人信息。
- 指标包含请求耗时、5xx、登录失败、版本冲突、上传失败和导出耗时。
- 健康检查区分存活状态和 MySQL、Redis、MinIO 就绪状态。

## 测试策略

- 单元测试：领域校验、权限、版本冲突、任务状态机。
- Web 切片测试：认证、参数校验、HTTP 状态和统一响应。
- 集成测试：MySQL、Redis、MinIO 与 Flyway，使用 Testcontainers。
- 契约测试：设计 JSON 示例和字段类型保持向后兼容。
- 验收测试：登录、模板复制、保存版本、图片上传和重新读取组成最小端到端链路。

## 部署

本地使用 Docker Compose 启动 MySQL、Redis、MinIO 和 RabbitMQ。Spring Boot 应用通过环境变量读取连接信息和密钥，镜像中不包含生产密码。生产环境由 Nginx 终止 TLS，MinIO 下载使用短期签名 URL。

