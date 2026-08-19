# 登录弹窗视觉还原 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将首页登录弹窗按四张参考图还原为四状态、双栏、可响应式使用的登录体验，同时保持现有手机号验证码接口和会话意图恢复不变。

**Architecture:** `LoginDialog.vue` 保留现有 `open/loading/error` props 和 `close/login` emits，内部用 `mode` 状态机渲染 `phone`、`qr`、`password`、`register` 四种视图。第三方品牌图标作为本地 SVG 资源由一个有序映射统一渲染；只有手机号状态提交真实登录事件，其他状态只改变视图或显示明确的后续开放提示。

**Tech Stack:** Vue 3 `<script setup>`, TypeScript, `lucide-vue-next`, Vite static SVG assets, Vitest + Testing Library Vue, Playwright。

---

### Task 1: Add local authentication icon assets

**Files:**
- Create: `poster-client/src/assets/auth-icons/phone.svg`
- Create: `poster-client/src/assets/auth-icons/qq.svg`
- Create: `poster-client/src/assets/auth-icons/weibo.svg`
- Create: `poster-client/src/assets/auth-icons/wechat.svg`
- Create: `poster-client/src/assets/auth-icons/dingtalk.svg`
- Create: `poster-client/src/assets/auth-icons/baidu.svg`
- Create: `poster-client/src/features/auth/auth-icons.ts`

- [x] **Step 1: Add the ordered icon map before changing the dialog**

  Define one exported immutable list in `auth-icons.ts`:

  ```ts
  import phoneIcon from '@/assets/auth-icons/phone.svg'
  import qqIcon from '@/assets/auth-icons/qq.svg'
  import weiboIcon from '@/assets/auth-icons/weibo.svg'
  import wechatIcon from '@/assets/auth-icons/wechat.svg'
  import dingtalkIcon from '@/assets/auth-icons/dingtalk.svg'
  import baiduIcon from '@/assets/auth-icons/baidu.svg'

  export const authProviderIcons = [
    { key: 'phone', label: '手机号登录', src: phoneIcon },
    { key: 'qq', label: 'QQ登录', src: qqIcon },
    { key: 'weibo', label: '微博登录', src: weiboIcon },
    { key: 'wechat', label: '微信登录', src: wechatIcon },
    { key: 'dingtalk', label: '钉钉登录', src: dingtalkIcon },
    { key: 'baidu', label: '百度登录', src: baiduIcon },
  ] as const
  ```

  Each SVG must be a self-contained 24x24 or 32x32 vector with no external references. Use the supplied reference ordering and recognizable brand marks; do not substitute text glyphs.

- [x] **Step 2: Verify the assets are valid static files**

  Run `rg -n "<svg|<path|<circle|<rect" poster-client/src/assets/auth-icons` and confirm all six files contain SVG markup. Run `npm run build` from `poster-client/` after the component work in Task 4 to verify Vite resolves every import.

- [ ] **Step 3: Commit the asset-only change**

  ```powershell
  git add poster-client/src/assets/auth-icons poster-client/src/features/auth/auth-icons.ts
  git commit -m "feat: add local login provider icons"
  ```

### Task 2: Lock the four-state behavior with failing tests

**Files:**
- Modify: `poster-client/src/features/auth/__tests__/LoginDialog.spec.ts`

- [x] **Step 1: Add a failing state-switch test**

  Render the dialog with `{ open: true }`, then assert phone mode starts with `手机验证码登录` and the following clicks expose the expected headings:

  ```ts
  await user.click(screen.getByRole('button', { name: '微信登录在这里' }))
  expect(screen.getByRole('heading', { name: '微信扫码安全登录' })).toBeVisible()
  await user.click(screen.getByRole('button', { name: '验证码登录在这里' }))
  await user.click(screen.getByRole('button', { name: '账号密码登录' }))
  expect(screen.getByRole('heading', { name: '账号密码登录' })).toBeVisible()
  await user.click(screen.getByRole('button', { name: '手机号验证码登录' }))
  await user.click(screen.getByRole('button', { name: '手机号码注册' }))
  expect(screen.getByRole('heading', { name: '注册账号' })).toBeVisible()
  ```

  Use the exact accessible names from the final buttons; the test must fail against the current two-state dialog because password and registration controls do not exist.

- [x] **Step 2: Add the icon-order and phone-submit tests**

  Assert the six `img` elements under the provider region have `alt` values `手机号登录`, `QQ登录`, `微博登录`, `微信登录`, `钉钉登录`, `百度登录` in that order. Fill the phone and verification inputs, submit the form, and assert the existing `login` emit contains `{ phone: '13800000000', verificationCode: '123456' }`.

- [x] **Step 3: Run the focused tests and verify RED**

  Run `npm run test:unit -- src/features/auth/__tests__/LoginDialog.spec.ts` from `poster-client/`. Expected result: existing phone/QR tests pass, and the new state/icon assertions fail because the implementation has not changed.

### Task 3: Implement the dialog state machine and contracts

**Files:**
- Modify: `poster-client/src/features/auth/LoginDialog.vue`
- Modify: `poster-client/src/features/home/WorkbenchShell.vue` only if the existing close/login event contract needs a type-only adjustment

- [x] **Step 1: Add the state and notice primitives**

  Keep the current props and emits. Add:

  ```ts
  type LoginMode = 'phone' | 'qr' | 'password' | 'register'
  const mode = ref<LoginMode>('phone')
  const localNotice = ref<string | null>(null)
  function setMode(next: LoginMode) {
    localNotice.value = null
    mode.value = next
  }
  function unsupported(label: string) {
    localNotice.value = `${label}将在后续阶段开放，请使用手机号验证码登录`
  }
  ```

  Reset `mode` and `localNotice` when `open` changes from false to true so reopening always starts in the phone mode.

- [x] **Step 2: Preserve the real phone login form**

  Keep `phone`, `verificationCode`, `submit`, `loading`, and `error` behavior unchanged. The form submit remains the only path that calls `emit('login', { phone, verificationCode })`; the loading label remains visible and the submit control stays disabled while loading.

- [x] **Step 3: Render all reference states with accessible controls**

  Replace the current two-tab switch with the reference-style top-right switch actions. Render:

  - Phone mode: phone and SMS-code fields, get-code control, login button, password switch, and provider row.
  - QR mode: QR frame, scan-help link, phone switch, and provider row.
  - Password mode: account and password fields, login button invoking `unsupported('账号密码登录')`, phone switch, forgot-password action, and provider row.
  - Register mode: phone, code, get-code, password, confirmation fields, and registration button invoking `setMode('phone')` plus the local notice.

  Use `authProviderIcons` with `<img :src="provider.src" :alt="provider.label" />`. Use Lucide icons for `X`, `QrCode`, `Smartphone`, `ArrowLeft`, and status actions; never render provider names as substitute glyphs.

- [x] **Step 4: Run the focused tests and verify GREEN**

  Run `npm run test:unit -- src/features/auth/__tests__/LoginDialog.spec.ts`. Expected result: all dialog tests pass, including the existing QR and phone-login contract tests and the new four-state/icon tests.

### Task 4: Rebuild the reference-aligned responsive styling

**Files:**
- Modify: `poster-client/src/features/auth/LoginDialog.vue` scoped style block

- [x] **Step 1: Implement the desktop layout tokens**

  Set the backdrop to a fixed full-screen dim layer with `backdrop-filter`; set the dialog to `width: min(1140px, calc(100vw - 64px))`, `min-height: 760px`, two columns `36% minmax(0, 64%)`, white surface, 10-12px radius, and the reference shadow. Match the blue promo panel, geometric repeated pattern, white wordmark, centered slogan, yellow underline, and six evenly spaced benefit rows.

- [x] **Step 2: Implement the right-side form geometry**

  Use a centered content column capped near 540px, stable 56px controls, 8-10px radii, blue primary button, dashed provider separator, six 46px provider tiles, and a bottom agreement strip. Place the close button and mode switch in the top-right safe area so they never overlap headings.

- [x] **Step 3: Add mobile constraints**

  At `max-width: 720px`, switch to one column, reduce promo height and padding, keep the form scrollable within the viewport, preserve stable button/input sizes, and hide only nonessential descriptive copy. Verify no horizontal overflow at 390px.

- [x] **Step 4: Run build-level checks**

  Run `npm run build` from `poster-client/`. Expected result: `vue-tsc --noEmit` and `vite build` both exit 0 with the local SVG assets bundled.

### Task 5: Verify visual behavior and regression coverage

**Files:**
- Modify: `poster-client/e2e/workbench.spec.ts` only if a login-dialog flow assertion is missing
- Test: `poster-client/src/features/auth/__tests__/LoginDialog.spec.ts`

- [x] **Step 1: Add desktop and mobile dialog checks**

  In Playwright, open the login dialog from the workbench at desktop and mobile viewports, assert the promo panel and phone form are visible, switch through QR/password/register, and verify the provider row contains six non-broken images with the expected accessible names.

- [x] **Step 2: Run the full frontend suite**

  Run `npm run test:unit`, `npm run build`, and `npm run test:e2e` from `poster-client/`. Expected result: all unit tests pass, the production build succeeds, and the existing workbench flows still pass on desktop and mobile.

- [x] **Step 3: Inspect the final diff and record the stage**

  Run `git diff --check` and inspect the changed LoginDialog, icon assets, tests, and plan. Confirm no API files, session store, or homepage data flow changed. Update `docs/progress/` with the login-dialog restoration and exact verification counts.

- [ ] **Step 4: Commit the completed implementation**

  ```powershell
  git add poster-client/src/features/auth poster-client/src/features/home/WorkbenchShell.vue poster-client/src/assets/auth-icons poster-client/e2e docs/progress
  git commit -m "feat: restore login dialog visual states"
  ```
