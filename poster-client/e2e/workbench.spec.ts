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
    if (url.pathname === '/api/v1/auth/login' && route.request().method() === 'POST') return response({ accessToken: 'e2e-token', tokenType: 'Bearer', expiresIn: 3600, userId: 1, tenantId: 1 })
    if (url.pathname === '/api/v1/designs' && route.request().method() === 'POST') return response({ id: 301, templateId: 1001, name: '夏日促销 · 我的设计', width: 1080, height: 1440, currentVersion: 1, schema: { schemaVersion: 1, pages: [] }, updatedAt: '2026-08-18T09:00:00Z' })
    return route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify({ code: 'NOT_FOUND', message: 'Not found', data: null }) })
  })
}

test.beforeEach(async ({ page }) => {
  await mockDiscoveryApi(page)
  await page.goto('/')
  await expect(page.getByRole('heading', { name: '模板库' })).toBeVisible()
})

test('creates a design after login and preserves the selected template intent', async ({ page }) => {
  await page.getByRole('button', { name: '打开夏日促销模板' }).click()
  await expect(page.getByRole('dialog')).toContainText('可编辑字段：1 项')
  await page.getByRole('button', { name: '使用此模板' }).click()

  await expect(page.getByRole('dialog', { name: '登录后继续' })).toBeVisible()
  await page.getByRole('textbox', { name: '手机号' }).fill('13800138000')
  await page.getByRole('textbox', { name: '验证码' }).fill('123456')
  await page.getByRole('button', { name: '登录并继续' }).click()

  await expect(page.getByRole('dialog', { name: '确认创建设计稿' })).toBeVisible()
  await page.getByRole('button', { name: '确认创建' }).click()
  await expect(page.getByRole('dialog', { name: '设计稿已创建' })).toBeVisible()
})

test('keeps future-phase navigation in place', async ({ page }) => {
  await page.locator('.mode-tabs .mode-tab').filter({ hasText: '智能创作' }).click()
  await expect(page.getByText('将在后续阶段开放', { exact: true })).toBeVisible()
  await expect(page).toHaveURL(/\/$/)
})

test('uses the mobile navigation without overflowing the template grid', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'mobile', 'Mobile-specific check')
  await expect(page.locator('.bottom-nav')).toBeVisible()
  await expect(page.locator('.template-grid')).toBeVisible()
  await expect(page.locator('.template-card')).toHaveCount(1)
  await page.screenshot({ path: testInfo.outputPath('workbench-mobile.png'), fullPage: true })
})
