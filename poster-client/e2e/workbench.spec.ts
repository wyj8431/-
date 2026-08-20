import { expect, test } from '@playwright/test'

const template = {
  id: 1001,
  name: '夏日促销',
  width: 1080,
  height: 1440,
  coverAssetId: null,
  coverUrl: null,
  categoryCode: 'marketing',
  tagCodes: ['promotion'],
  publishedAt: '2026-08-17T09:00:00Z',
}

async function mockDiscoveryApi(page: import('@playwright/test').Page) {
  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url())
    const response = (data: unknown) => route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 'OK', message: 'OK', data }) })

    if (url.pathname === '/api/v1/home') {
      return response({
        trendingTags: [{ code: 'promotion', name: '促销' }, { code: 'festival', name: '节日海报' }],
        featuredTemplates: [template],
        hotspotCalendar: [{ code: 'mid-autumn', title: '中秋预热', startsAt: '2026-09-01T00:00:00Z', templateCount: 8 }],
        editorialScenes: [{ code: 'summer-promotion', title: '夏日促销', subtitle: '清爽的夏季营销版式', coverAssetId: null, coverUrl: null }],
      })
    }
    if (url.pathname === '/api/v1/template-categories') return response([{ code: 'marketing', name: '营销推广' }])
    if (url.pathname === '/api/v1/templates') return response({ items: [template], page: 1, pageSize: 24, total: 1 })
    if (url.pathname === '/api/v1/templates/1001') return response({ ...template, schema: { schemaVersion: 1, canvas: { width: 1080, height: 1440 }, pages: [] }, fields: [{ fieldKey: 'title', label: '主标题', fieldType: 'TEXT', required: true, defaultValue: '' }] })
    if (url.pathname === '/api/v1/admin/summary') return response({ memberCount: 2, activeUserCount: 2, auditCount: 3, health: 'UP' })
    if (url.pathname === '/api/v1/admin/users') return response({
      items: [
        { userId: 1, phoneMasked: '138****0000', tenantRole: 'ADMIN', userStatus: 'ACTIVE', joinedAt: '2026-08-18T09:00:00Z' },
        { userId: 2, phoneMasked: '139****0000', tenantRole: 'USER', userStatus: 'ACTIVE', joinedAt: '2026-08-18T10:00:00Z' },
      ],
      page: 1,
      pageSize: 100,
      total: 2,
    })
    if (url.pathname === '/api/v1/auth/login' && route.request().method() === 'POST') return response({ accessToken: 'e2e-token', tokenType: 'Bearer', expiresIn: 3600, userId: 1, tenantId: 1, tenantRole: 'ADMIN' })
    if (url.pathname === '/api/v1/auth/wechat/login/authorize' && route.request().method() === 'POST') return response({ authorizeUrl: '/wechat-test-authorize' })
    if (url.pathname === '/api/v1/designs' && route.request().method() === 'POST') return response({ id: 301, templateId: 1001, name: '夏日促销 · 我的设计', width: 1080, height: 1440, currentVersion: 1, schema: { schemaVersion: 1, pages: [] }, updatedAt: '2026-08-18T09:00:00Z' })
    return route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify({ code: 'NOT_FOUND', message: 'Not found', data: null }) })
  })
}

test.beforeEach(async ({ page }, testInfo) => {
  await mockDiscoveryApi(page)
  await page.goto('/')
  await expect(page.getByRole('heading', { name: '模板库' })).toBeVisible()
  await page.screenshot({ path: testInfo.outputPath('workbench-home.png'), fullPage: false })
})

test('creates a design after login and preserves the selected template intent', async ({ page }, testInfo) => {
  await page.getByRole('button', { name: '打开夏日促销模板' }).click()
  await expect(page.getByRole('dialog')).toContainText('可编辑字段：1 项')
  await page.getByRole('button', { name: '使用此模板' }).click()

  await expect(page.getByRole('dialog', { name: '登录后继续' })).toBeVisible()
  await page.getByRole('textbox', { name: '手机号' }).fill('13800138000')
  await page.getByRole('textbox', { name: '验证码' }).fill('123456')
  await page.getByRole('button', { name: '登录', exact: true }).click()

  await expect(page.getByRole('dialog', { name: '确认创建设计稿' })).toBeVisible()
  await page.getByRole('button', { name: '确认创建' }).click()
  await expect(page.getByRole('dialog', { name: '设计稿已创建' })).toBeVisible()
  await page.screenshot({ path: testInfo.outputPath('workbench-desktop-or-mobile.png'), fullPage: false })
})

test('starts the server-authorized WeChat handoff and retains supported dialog modes', async ({ page }) => {
  await page.getByRole('button', { name: '打开夏日促销模板' }).click()
  await page.getByRole('button', { name: '使用此模板' }).click()

  const dialog = page.getByRole('dialog', { name: '登录后继续' })
  await expect(dialog).toBeVisible()
  await dialog.screenshot({ path: test.info().outputPath('login-dialog-phone.png') })
  await page.screenshot({ path: test.info().outputPath('login-dialog-page.png') })
  await expect(dialog.locator('img').first()).toHaveAttribute('alt', '手机号登录')
  await expect(dialog.locator('img')).toHaveCount(6)
  await expect(dialog.locator('img').evaluateAll((images) => images.map((image) => image.getAttribute('alt')))).resolves.toEqual([
    '手机号登录', 'QQ登录', '微博登录', '微信登录', '钉钉登录', '百度登录',
  ])

  await dialog.getByRole('button', { name: '微信登录在这里' }).click()
  await expect(page).toHaveURL(/\/wechat-test-authorize$/)
  await page.goto('/')
  await page.getByRole('button', { name: '打开夏日促销模板' }).click()
  await page.getByRole('button', { name: '使用此模板' }).click()
  const reopenedDialog = page.getByRole('dialog', { name: '登录后继续' })
  await reopenedDialog.getByRole('button', { name: '账号密码登录' }).click()
  await expect(reopenedDialog.getByRole('heading', { name: '账号密码登录' })).toBeVisible()
  await reopenedDialog.getByRole('button', { name: '手机号验证码登录' }).click()
  await reopenedDialog.getByRole('button', { name: '手机号码注册' }).click()
  await expect(reopenedDialog.getByRole('heading', { name: '注册账号' })).toBeVisible()
  await reopenedDialog.screenshot({ path: test.info().outputPath('login-dialog-register.png') })
})

test('keeps future-phase navigation in place', async ({ page }) => {
  await page.locator('.mode-tabs .mode-tab').filter({ hasText: 'Agent 模式' }).click()
  await expect(page.getByText('将在后续阶段开放', { exact: true })).toBeVisible()
  await expect(page).toHaveURL(/\/$/)
})

test('loads the tenant member management view after administrator login', async ({ page }) => {
  await page.getByRole('button', { name: '登录注册' }).click()
  const dialog = page.getByRole('dialog', { name: '登录后继续' })
  await dialog.getByRole('textbox', { name: '手机号' }).fill('13800138000')
  await dialog.getByRole('textbox', { name: '验证码' }).fill('123456')
  await dialog.getByRole('button', { name: '登录', exact: true }).click()

  await page.getByRole('button', { name: '账户中心' }).click()
  await page.getByRole('button', { name: '团队管理' }).click()
  await expect(page).toHaveURL(/\/admin$/)
  await expect(page.getByRole('heading', { name: '租户成员' })).toBeVisible()
  await expect(page.getByText('138****0000')).toBeVisible()
  await expect(page.getByRole('combobox', { name: '修改 139****0000 的角色' })).toHaveValue('USER')
})

test('uses the mobile navigation without overflowing the template grid', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'mobile', 'Mobile-specific check')
  await expect(page.locator('.bottom-nav')).toBeVisible()
  await expect(page.locator('.template-grid')).toBeVisible()
  await expect(page.locator('.template-card')).toHaveCount(1)
  await page.screenshot({ path: testInfo.outputPath('workbench-mobile.png'), fullPage: true })
})
