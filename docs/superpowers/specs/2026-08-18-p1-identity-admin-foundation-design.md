# P1 身份与后台基础设计规格

**日期：** 2026-08-18  
**状态：** 待用户审阅  
**基线：** P0 工作台与模板发现已完成

## 1. 目标

为 All+poster 建立可撤销、可恢复的登录会话，以及最小可用的平台运营后台入口。首个 P1 纵切完成后，用户可以在短期访问令牌过期后无感恢复会话，账号退出或被撤销后不能继续刷新；服务端能够区分租户内的普通用户、运营和管理员；具备 `OPERATOR` 或 `ADMIN` 租户角色的用户可以进入一个受权限保护的管理端壳。租户成员角色为 `ADMIN`、`USER`、`OPERATOR`，设计稿 ACL 的 `OWNER`、`EDITOR`、`VIEWER` 保持独立。

## 2. 范围

### 本次包含

- 服务端刷新令牌的签发、轮换、撤销、过期清理和重放检测。
- 手机号验证码首次验证成功时自动注册账号并创建默认团队；后续使用同一登录接口，不单独提供注册页面或注册接口。
- 浏览器端会话恢复、并发请求下的单次刷新、401 后重试一次、统一登出和登录后返回原意图。
- 租户成员角色 `USER`、`OPERATOR`、`ADMIN` 的数据模型、JWT 权限映射和服务端接口守卫。
- 最小管理端壳：受保护的 `/admin` 路由、当前用户/角色信息、健康状态和审计摘要入口；不实现模板、分类或素材 CRUD。
- 审计日志模型与关键身份事件记录：登录成功/失败、刷新成功/失败、登出、刷新令牌重放、租户成员角色变更。
- OpenAPI 文档、参数校验、统一错误响应和单元/集成/E2E 验收。

### 本次不包含

- 微信登录真实适配、扫码回调和第三方凭据配置；只保留后续适配器的端口设计。
- 完整运营后台页面、模板/素材/运营位管理、用户批量操作和报表。
- 会员、支付、实时协作、客服、编辑器和自由画布。
- 把刷新令牌或访问令牌写入 `localStorage`、`sessionStorage` 或前端 Pinia 持久化插件。

## 3. 设计决策

### 3.1 会话模型

访问令牌继续使用 HS256 JWT，默认有效期保持 15 分钟；刷新令牌改为高熵随机不透明字符串，仅通过 `HttpOnly`、`Secure`、`SameSite=Lax` Cookie 传输。服务端只保存刷新令牌的 SHA-256 摘要，不保存明文。浏览器端只在内存中保存访问令牌及其过期时间。

每次刷新都执行令牌轮换：旧令牌标记为已替换，新令牌属于同一个 token family。已替换或已撤销令牌再次使用时，服务端撤销该 family 下所有令牌并返回 `UNAUTHORIZED`，防止被窃取的旧令牌继续使用。退出登录撤销当前令牌；“退出全部设备”撤销用户所有 family，并递增用户的 `security_version`。

JWT 增加 `tenantRole` 和现有 `tenantId` 声明。租户成员角色映射为 Spring Security 的 `ROLE_USER`、`ROLE_OPERATOR`、`ROLE_ADMIN`。访问令牌短期有效，角色变更会撤销该租户的刷新令牌；已签发的访问令牌最多在其剩余 TTL 内有效，后台界面不把前端隐藏当作授权边界。

### 3.2 租户成员角色

租户角色直接保存在 `sys_tenant_member.role`，同一用户在不同租户可以拥有不同角色，不新增全局 `sys_user.platform_role`：

| 角色 | 能力 | 不能做的事 |
| --- | --- | --- |
| `USER` | 使用 ToC 工作台、查看自己的会话信息 | 进入 `/admin`、查看审计、修改租户成员角色 |
| `OPERATOR` | 进入管理端壳、查看健康状态和审计摘要 | 修改租户成员角色、读取商家私有设计稿 |
| `ADMIN` | 拥有运营能力并可调整租户成员角色、撤销租户会话 | 绕过租户 ACL 读取私有设计稿 |

租户成员角色与设计稿 ACL 分别校验。管理端接口先检查当前租户成员角色，再执行资源级规则；设计稿接口仍只通过租户和设计稿 ACL 授权。

### 3.3 管理端壳

新增 `/admin` 路由和懒加载 `AdminShell.vue`。路由进入时先恢复会话，再检查当前租户成员是否为 `OPERATOR` 或 `ADMIN`；未登录返回 `/` 并保留 `returnTo=/admin`，已登录但无权限显示 403 页面而不是重定向循环。首屏只展示当前账号、租户角色、API 健康状态和最近审计事件数量，所有后续管理菜单以不可用状态呈现并明确属于后续 P1 子阶段。

## 4. 数据模型

新增 Flyway `V3__tenant_member_roles.sql`，不修改已发布的 V1/V2。

### `sys_user` 会话版本字段（刷新令牌子阶段）

刷新令牌子阶段仍需使用 `sys_user.security_version` 支持“退出全部设备”和账号级会话失效；该字段与租户成员角色独立，不是平台角色字段。本次角色迁移不新增该字段。

```sql
ALTER TABLE sys_user
    ADD COLUMN security_version INT NOT NULL DEFAULT 0 AFTER status,
    ADD CONSTRAINT chk_user_security_version CHECK (security_version >= 0);
```

### `sys_tenant_member.role` 迁移

V3 先移除 V1 的角色约束，把历史 `OWNER` 映射为 `ADMIN`、`MEMBER` 映射为 `USER`，再添加 `ADMIN / USER / OPERATOR` 约束。默认团队的首个成员角色为 `ADMIN`。

### `auth_refresh_token`

```sql
CREATE TABLE auth_refresh_token (
    id BIGINT NOT NULL AUTO_INCREMENT,
    token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    family_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    security_version INT NOT NULL,
    replaced_by_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    expires_at DATETIME(6) NOT NULL,
    last_used_at DATETIME(6) NULL,
    revoked_at DATETIME(6) NULL,
    revoke_reason VARCHAR(64) NULL,
    user_agent VARCHAR(512) NULL,
    ip_address VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_token_hash (token_hash),
    KEY idx_refresh_family (family_id),
    KEY idx_refresh_user_active (user_id, revoked_at, expires_at),
    CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_refresh_tenant_member FOREIGN KEY (tenant_id, user_id)
        REFERENCES sys_tenant_member (tenant_id, user_id),
    CONSTRAINT chk_refresh_security_version CHECK (security_version >= 0)
);
```

### `sys_audit_log`

```sql
CREATE TABLE sys_audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    actor_user_id BIGINT NULL,
    tenant_id BIGINT NULL,
    action VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(128) NULL,
    outcome VARCHAR(16) NOT NULL,
    request_id VARCHAR(64) NULL,
    metadata_json JSON NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_audit_created (created_at),
    KEY idx_audit_actor_created (actor_user_id, created_at),
    KEY idx_audit_action_created (action, created_at),
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_user_id) REFERENCES sys_user (id),
    CONSTRAINT chk_audit_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE'))
);
```

`metadata_json` 只允许结构化的非敏感元数据，例如登录方式、客户端类型和失败错误码；禁止写入验证码、Cookie、访问令牌、刷新令牌、完整手机号和文件内容。手机号在审计中最多保留后四位。

## 5. API 契约

所有接口继续使用现有 `ApiResponse<T>` 包装和 `Trace-Id`。Cookie 名称固定为 `poster_refresh_token`，刷新接口只接受 Cookie，不接受 JSON 中的令牌。

### `POST /api/v1/auth/refresh`

- 认证：刷新 Cookie。
- 成功 `200`：`{ accessToken, tokenType: "Bearer", expiresIn, userId, tenantId, tenantRole }`，并设置轮换后的刷新 Cookie。
- Cookie 缺失、过期、撤销、重放或账号状态异常：`401 UNAUTHORIZED`，同时清理 Cookie。
- 事务内锁定旧令牌行，完成校验、替换和新令牌写入；并发刷新只有一个成功，其他请求得到 `401` 并触发前端重新登录。

### `POST /api/v1/auth/logout`

- 认证：可选访问令牌和刷新 Cookie。
- 成功 `204` 或现有空数据成功包，撤销当前 family 并清理 Cookie。
- 重复登出保持幂等，不泄露令牌是否曾经有效。

### `GET /api/v1/auth/me`

- 认证：Bearer 访问令牌。
- 成功：`{ userId, tenantId, phoneMasked, tenantRole }`。
- 数据从当前用户和当前租户成员读取，不仅信任 JWT 中的角色声明。

### `GET /api/v1/admin/summary`

- 认证：当前租户成员角色为 `ROLE_OPERATOR` 或 `ROLE_ADMIN`。
- 成功：`{ tenantRole, health: { api: "UP" }, audit: { recentEvents } }`。
- `USER` 得到 `403 FORBIDDEN`；未登录得到 `401 UNAUTHORIZED`。
- 不返回私有设计稿、模板草稿或完整用户列表。

### `PATCH /api/v1/admin/users/{userId}/tenant-role`

- 认证：当前租户成员角色为 `ROLE_ADMIN`。
- 请求：`{ tenantRole: "USER" | "OPERATOR" | "ADMIN" }`。
- 成功：返回用户的 `userId`、新租户角色，撤销该用户在当前租户的刷新令牌。
- 不能把当前租户最后一个 `ADMIN` 降级；目标用户不存在、已禁用或角色值非法分别返回统一错误码。
- 记录成功或失败审计事件；该接口只允许改变当前租户的成员角色，不得改变设计稿 ACL。

现有 `POST /api/v1/auth/login` 保持兼容，但返回值增加 `tenantRole`，成功登录时同时签发刷新 Cookie并创建审计事件。手机号验证码登录仍使用本地验证器，微信端口在后续子阶段接入。

手机号账号采用无感注册：当手机号不存在且验证码校验成功时，事务内创建 `sys_user`、默认 `sys_tenant` 和 `ADMIN` 成员关系，再签发会话；当手机号已存在时只执行账号、团队和成员角色状态校验。验证码错误、手机号格式错误或账号/团队被禁用时都不会创建新账号。

## 6. 前端数据流

1. `main.ts` 创建 Pinia 后，路由守卫调用 `session.restore()`；若内存中没有访问令牌则尝试一次 `/auth/refresh`。
2. `session` store 保存访问令牌、过期时间、用户/租户/租户角色和恢复状态，不持久化令牌。
3. `api/http.ts` 收到 `401` 时调用去重后的 `session.refresh()`，刷新成功后仅重试原请求一次；刷新失败则清空会话并触发路由跳转。
4. 登录成功同时保存原有待执行模板意图和 `returnTo`，刷新页面或令牌轮换不丢失意图。
5. 路由元信息使用 `meta.requiresAuth` 和 `meta.tenantRoles`；页面隐藏菜单只用于体验，所有后台请求仍由服务端鉴权。
6. `/admin` 使用 `AdminShell.vue`，在加载态展示骨架；无权限显示 403；API 失败显示可重试状态，不把失败误报为“无数据”。

## 7. 错误与安全处理

- 刷新令牌 Cookie 不开放给 JavaScript，不进入日志、异常消息或前端错误上报。
- CORS 只允许现有配置的明确来源；刷新/登出接口仅接受 `POST`，不接受跨站 GET。
- 使用恒定时间比较 token hash；刷新令牌使用 `SecureRandom` 生成，原始值至少 32 字节。
- 账号禁用、租户禁用、`security_version` 不匹配都立即拒绝刷新，并清理 Cookie。
- 审计写入失败不能让登出失败，但登录成功、角色变更和重放检测的审计写入失败必须记录服务端错误并让操作失败，避免产生不可追踪的权限变更。
- 所有管理端接口使用统一 `401/403/422` 响应；不通过状态码或消息泄露用户存在性。

## 8. 测试与验收

### 服务端

- `JwtTokenServiceTest`：角色/版本声明、过期时间、短 secret 拒绝。
- `RefreshTokenServiceTest`：签发、轮换、过期、撤销、重放检测、同 family 全量撤销。
- `AuthServiceTest`：登录返回刷新 Cookie 所需结果、禁用用户拒绝、租户角色返回。
- `AdminAuthorizationTest`：USER 得到 403，OPERATOR 可读取 summary 但不能改角色，ADMIN 可改角色且最后一个 ADMIN 不可降级。
- `AuditLogServiceTest`：敏感字段不落库，成功/失败事件和 request id 正确记录。
- `AuthSecurityTest` / `BackendVerticalSliceIT`：真实 HTTP 流程覆盖登录、刷新、登出、401、403、角色变更后刷新失败和租户 ACL 不越权。

### 前端

- session store：restore、并发 refresh 去重、refresh 失败清理、待执行意图保留。
- HTTP client：401 只重试一次，刷新失败不会无限循环，原请求的 method/body 保留。
- router：`/admin` 的未登录跳转、USER 403、OPERATOR/ADMIN 放行和 `returnTo` 恢复。
- 管理端壳：加载、成功、403、API 错误重试四种状态。
- Playwright：短 TTL 模拟过期后仍能访问工作台；登出后访问 `/admin` 回到首页；普通用户访问 `/admin` 显示 403；管理员可看到 summary。

### 验收标准

- 访问令牌过期时，用户无需重新输入验证码即可完成一次合法请求。
- 同一刷新令牌并发请求最多产生一个新会话；旧令牌再次使用会让整组刷新令牌失效。
- 浏览器存储中不存在访问令牌或刷新令牌明文。
- 普通用户无法通过直接请求管理端 API 读取 summary 或修改角色。
- 修改租户成员角色会撤销目标用户在当前租户的刷新会话，且不改变设计稿 ACL。
- P0 的模板搜索、详情和设计稿创建流程保持通过。

## 9. 交付顺序

1. 先提交 V2 数据库迁移和仓储接口，再用服务端单元测试固定刷新/角色/审计行为。
2. 实现刷新令牌服务、登录/刷新/登出/me API 和 JWT 角色转换，补充集成测试。
3. 实现最小后台 summary 与角色变更 API，补齐权限和审计测试。
4. 改造前端 session/http/router，保留 P0 登录意图恢复；加入 AdminShell 和 Playwright 验收。
5. 更新 Swagger、README、本地运行说明和阶段记录，运行服务端与前端完整验证。

## 10. 后续 P1 子阶段

规格完成后再单独设计并实现：微信登录适配器、租户成员/模板/分类/素材管理、审计查询筛选、角色管理页面和 ToB 管理端导航。每个子阶段必须复用本规格定义的刷新令牌、租户角色、审计和租户 ACL 边界。
