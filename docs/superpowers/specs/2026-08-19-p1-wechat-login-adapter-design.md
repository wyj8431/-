# P1 微信登录适配器与账号绑定设计

## 1. 目标与边界

本子项目为现有手机号账户增加微信网站扫码登录适配器。手机号仍是账户的主身份；微信只可登录已绑定的账户，不能通过首次扫码自动创建账户或自动合并账户。

本设计不包含解绑、手机号换绑、微信昵称或头像同步、微信令牌持久化、真实凭据配置、生产发布、P2 编辑器、Schema 编辑、商家自由画布或其他平台素材流程。

## 2. 已确认的用户规则

1. 未登录访问者点击微信登录后进入微信网站扫码授权。
2. 微信账号已绑定至有效平台用户时，授权完成后签发当前项目的访问令牌和 HttpOnly 刷新令牌 Cookie，并返回原站。
3. 微信账号未绑定时，不创建用户、不合并手机号账户、不签发会话；前端提示用户先用手机号登录，再在个人中心绑定微信。
4. 已登录用户在个人中心发起绑定，完成微信授权后将微信 OpenID 绑定到当前用户。
5. 一个 OpenID 只能绑定一个用户；目标 OpenID 已绑定其他用户时返回冲突，原绑定保持不变。
6. 已禁用用户或已禁用租户不能通过微信获得会话；绑定操作需要有效 JWT 和有效当前身份。

## 3. 模块边界

`auth` 模块新增以下职责：

- `WechatOAuthProvider`：网站扫码授权地址构造及授权码交换端口。真实实现使用现有 OkHttp，并限制连接和读取超时；本地和测试使用可预测的替身实现。
- `WechatOAuthStateService`：创建、校验并一次性消费 OAuth state；它不把原始 state 写入数据库。
- `WechatAuthService`：编排登录、绑定、账户状态校验、会话签发与审计。它复用 `AuthService` 现有身份校验和 `RefreshTokenService` 会话签发逻辑，不能让 Controller 直接访问 Mapper。
- `WechatAuthController`：仅做 HTTP 请求解析、当前用户提取、Cookie 写入和固定站内重定向。

前端增加微信授权启动函数、回调结果路由页和个人中心绑定动作。访问令牌仅保留在现有 Pinia 内存状态；刷新令牌只经 HttpOnly Cookie 传递。

## 4. 数据模型与迁移

新增非破坏性 Flyway V7 表 `auth_wechat_oauth_state`：

| 列 | 规则 |
| --- | --- |
| `id` | `BIGINT` 自增主键 |
| `state_hash` | 原始随机 state 的 SHA-256 哈希；唯一，ASCII 二进制比较 |
| `purpose` | `LOGIN` 或 `BIND` |
| `initiator_user_id` | `BIND` 必填，`LOGIN` 为 `NULL` |
| `initiator_tenant_id` | `BIND` 必填，`LOGIN` 为 `NULL`；与用户一起锁定发起绑定的当前租户身份 |
| `return_path` | 经白名单规范化后的站内路径，最大 512 字符 |
| `expires_at` | 创建后 5 分钟到期 |
| `consumed_at` | 首次回调原子消费时间；后续回调均拒绝 |
| `created_at` | 记录创建时间 |

表索引包括 `state_hash` 唯一索引和 `(expires_at, consumed_at)` 清理索引。`(initiator_tenant_id, initiator_user_id)` 作为复合外键引用 `sys_tenant_member(tenant_id, user_id)`，因此绑定回调只能验证并使用发起时的同一租户成员关系；`purpose` 检查约束保证 `LOGIN` 两列均为空、`BIND` 两列均非空。此迁移不删除或修改既有数据。继续复用 `sys_user.wechat_open_id` 的唯一约束，不保存授权码、微信短期 access token、refresh token、昵称或头像。

## 5. 配置与提供商启用

所有微信配置仅在后端环境变量中读取：

```text
WECHAT_LOGIN_ENABLED=false
WECHAT_OPEN_APP_ID=
WECHAT_OPEN_APP_SECRET=
WECHAT_OPEN_REDIRECT_URI=
WEB_PUBLIC_BASE_URL=http://localhost:5173
```

真实提供商只在启用开关为真且 AppID、AppSecret、HTTPS 回调地址完整时创建。`AppSecret` 不写入响应、日志、审计字段或前端构建产物。未配置时服务正常启动，授权入口返回 `503 WECHAT_LOGIN_UNAVAILABLE`。

`WECHAT_OPEN_REDIRECT_URI` 是微信平台注册的固定回调地址。`WEB_PUBLIC_BASE_URL` 只接受 HTTP(S) 的单一站点基地址，回调后重定向只会发生到该基地址下的固定 `/auth/wechat/result` 页面，不能由调用方指定主机或协议。

## 6. HTTP 契约

### `POST /api/v1/auth/wechat/login/authorize`

公开接口。请求可选 `returnTo`，省略时默认为 `/`；给定时仅接受以单个 `/` 开头、非 `//`、不含协议或反斜杠的站内路径。给定的无效值直接返回 `400 VALIDATION_ERROR`，不会回退或尝试解释为外部地址。成功响应：

```json
{
  "code": "OK",
  "message": "success",
  "data": { "authorizeUrl": "https://open.weixin.qq.com/..." },
  "traceId": "..."
}
```

### `POST /api/v1/auth/wechat/bind/authorize`

要求现有 Bearer JWT。服务端先从数据库解析并校验当前用户、当前租户和成员角色，再创建用途为 `BIND` 的 state。成功响应与登录授权接口相同。`initiator_user_id` 和 `initiator_tenant_id` 必须来自 JWT 和数据库身份，不能来自请求体。

### `GET /api/v1/auth/wechat/callback?code={code}&state={state}`

公开的固定回调地址，仅接收微信 `code` 与 `state`。服务端按以下顺序处理：

1. 校验格式并对 state 哈希查询，加锁后一次性消费。已消费、过期、不存在或用途不匹配不调用提供商、不设置 Cookie，并重定向为固定结果 `expired`。
2. 调用提供商以 `code` 换取 OpenID；使用固定 URL、连接和读取超时，不跟随调用方给出的地址。
3. `LOGIN`：按 OpenID 查询用户。无绑定时写入固定结果 `unbound`；有绑定时校验用户、租户和角色，签发当前会话，并设置刷新 Cookie。
4. `BIND`：以 state 中的原始用户与租户 ID 重新验证用户状态、团队状态和成员角色仍有效，将 OpenID 原子绑定到该用户；唯一约束冲突写入固定结果 `already_bound`。
5. 仅跳转至 `${WEB_PUBLIC_BASE_URL}/auth/wechat/result?result={fixed-result}&returnTo={encoded-return-path}`。回调 URL 不带 access token、refresh token、授权码、OpenID、微信错误描述或内部错误细节。

固定结果为 `success`、`unbound`、`already_bound`、`cancelled`、`failed` 或 `expired`。两个授权启动接口的请求校验错误使用项目统一错误信封；浏览器回调无论成功、state 失败或提供商失败都只跳转固定结果页，且不泄漏提供商响应。

### `GET /api/v1/auth/me`

现有响应向后兼容新增：

```json
{ "wechatBound": true }
```

### 错误码

| HTTP | 错误码 | 含义 |
| --- | --- | --- |
| 503 | `WECHAT_LOGIN_UNAVAILABLE` | 微信真实适配器未启用或配置不完整 |
| 409 | `WECHAT_ACCOUNT_ALREADY_BOUND` | OpenID 已绑定至另一用户 |
| 401 | `UNAUTHORIZED` | 绑定调用缺少或失效会话 |
| 400 | `VALIDATION_ERROR` | 请求参数或站内返回路径无效 |

## 7. 会话、安全与审计

- state 使用密码学安全随机值，数据库只保存 SHA-256 哈希，5 分钟有效，并在同一事务中锁定后标记消费，防止 CSRF 和重放。
- Cookie 继续复用 `poster_refresh_token`、`HttpOnly`、`SameSite=Lax`、`/api/v1/auth` path 和现有 secure 配置。不会把刷新令牌或访问令牌置于 URL、localStorage、sessionStorage 或 Pinia 持久化中。
- `returnTo` 只用作前端回跳提示，服务端强制白名单规范化，防止开放重定向。
- 提供商调用不记录 `code`、OpenID、access token、secret 或完整响应体。审计 metadata 只记录用途和固定结果。
- 写入成功的操作记录 `WECHAT_LOGIN` 或 `WECHAT_BIND` 审计事件；登录拒绝、未绑定和绑定冲突记录无敏感数据的失败审计事件。

## 8. 前端行为

1. 登录弹窗中的微信入口调用 `login/authorize` 后，以 `window.location.assign(authorizeUrl)` 进入官方扫码页；二维码模式不再伪造可登录的静态二维码。
2. 个人中心根据 `me.wechatBound` 显示“已绑定”或“未绑定”。未绑定状态的“绑定”调用受保护授权接口并跳转。
3. `/auth/wechat/result` 读取固定 `result` 和已规范化的 `returnTo`。`success` 时调用现有 refresh 流程恢复内存访问令牌和身份，然后回到 `returnTo`；其他结果显示固定中文提示并提供返回工作台动作。
4. 回调结果页不解析或储存任何微信身份资料、OAuth code 或令牌。

## 9. 验证范围

后端单元测试覆盖：

- state 哈希、不存在、过期、重复消费和用途隔离；
- 已绑定登录的会话签发、未绑定拒绝、禁用用户和禁用租户拒绝；
- 绑定成功、唯一约束冲突、发起用户停用、原始团队停用和原始成员关系失效；
- 固定回调重定向、站内 return path 规范化和审计脱敏；
- `me.wechatBound` 的真/假响应。

MockMvc 或集成测试覆盖公开/受保护接口授权、错误码、刷新 Cookie 设置、回调无令牌泄漏和 V7 Flyway 迁移。前端单元测试覆盖授权跳转、个人中心绑定状态、回调成功恢复会话，以及 `unbound`、`already_bound` 和失败提示。完成后运行后端完整测试、前端完整测试和生产构建、后端 package、`git diff --check`；真实微信回调只在用户配置凭据后手动验收。

## 10. 明确的交付边界

本次只交付可测试、默认关闭真实提供商的微信身份适配器。没有开放平台凭据时，产品不会伪装为可完成真实扫码登录；授权入口返回明确的不可用错误。配置正确的凭据、固定 HTTPS 回调地址和前端站点地址后，无需修改前端凭据或迁移数据即可启用真实提供商。
