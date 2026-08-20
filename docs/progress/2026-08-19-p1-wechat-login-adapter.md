# P1 微信登录适配器与账号绑定

## Delivered

- 新增微信网站授权启动、固定回调、已绑定账号登录和个人中心绑定流程。
- 微信首次扫码只会提示先使用手机号登录并绑定，不会自动创建或合并平台账号。
- 前端只在 Pinia 内存中保留访问令牌；回调通过现有 HttpOnly 刷新 Cookie 恢复会话。

## Security Decisions

- V7 仅保存 OAuth state 的 SHA-256 哈希，state 有五分钟有效期并且只能消费一次。
- 绑定 state 固定保存发起时的 userId 和 tenantId；回调会重新验证同一租户身份。
- 回调仅跳转到固定的站内结果页，URL 不包含访问令牌、刷新令牌、授权码或 OpenID。
- 审计事件只记录用途和固定结果，不记录 OpenID、授权码、令牌、凭据或完整提供商响应。
- 微信 OpenID 只能绑定一个平台用户，绑定冲突不会修改原绑定。

## Verification

- `WechatOAuthStateServiceTest` 覆盖 state 哈希、过期和一次性消费。
- `WechatAuthServiceTest` 覆盖已绑定登录、未绑定拒绝、租户重新校验和 state 重放。
- `WechatAuthControllerTest` 覆盖 Cookie 设置、回调 URL 脱敏、受保护的绑定入口和返回路径校验。
- 前端单元测试覆盖服务端授权 URL、授权入口事件、绑定状态和回调结果页；Playwright 覆盖桌面及移动端授权跳转。

## Configuration Required for Real WeChat

真实扫码默认关闭。仅在服务器环境中配置 `WECHAT_LOGIN_ENABLED=true`、`WECHAT_OPEN_APP_ID`、`WECHAT_OPEN_APP_SECRET`、固定 HTTPS `WECHAT_OPEN_REDIRECT_URI` 和 `WEB_PUBLIC_BASE_URL` 后启用。凭据不进入前端构建产物、日志或本文件。

## Explicit Exclusions

- 未提供解绑、手机号换绑、微信昵称或头像同步。
- 未配置真实凭据，未进行真实微信 OAuth 交换或生产迁移。
- 不包含 P2 编辑器、自由画布或其他第三方登录方式。
