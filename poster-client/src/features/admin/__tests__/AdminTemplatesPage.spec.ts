import { fireEvent, render, screen, waitFor } from '@testing-library/vue'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const adminApi = vi.hoisted(() => ({
  loadAdminTemplates: vi.fn(),
  changeAdminTemplateStatus: vi.fn(),
  createAdminTemplate: vi.fn(),
  updateAdminTemplate: vi.fn(),
  deleteAdminTemplate: vi.fn(),
  loadAdminTemplateCategories: vi.fn(),
  loadAdminTemplateTags: vi.fn(),
  loadAdminTemplateCoverAssets: vi.fn(),
  bindAdminTemplateCover: vi.fn(),
}))
vi.mock('@/api/admin', () => adminApi)

import AdminTemplatesPage from '@/features/admin/AdminTemplatesPage.vue'
import { ApiError } from '@/api/http'
import { useSessionStore } from '@/stores/session'

describe('AdminTemplatesPage', () => {
  let pinia: ReturnType<typeof createPinia>

  beforeEach(() => {
    pinia = createPinia()
    setActivePinia(pinia)
    adminApi.loadAdminTemplates.mockReset()
    adminApi.changeAdminTemplateStatus.mockReset()
    adminApi.createAdminTemplate.mockReset()
    adminApi.updateAdminTemplate.mockReset()
    adminApi.deleteAdminTemplate.mockReset()
    adminApi.loadAdminTemplateCategories.mockReset()
    adminApi.loadAdminTemplateTags.mockReset()
    adminApi.loadAdminTemplateCoverAssets.mockReset()
    adminApi.bindAdminTemplateCover.mockReset()
    adminApi.loadAdminTemplateCoverAssets.mockResolvedValue([])
    adminApi.bindAdminTemplateCover.mockImplementation((templateId: number, coverAssetId: number | null) => Promise.resolve({ templateId, coverAssetId }))
  })

  it('allows an administrator to filter and change a template status', async () => {
    adminApi.loadAdminTemplates.mockResolvedValue([{ id: 1001, name: '朋友圈促销', width: 1080, height: 1440, categoryCode: 'marketing', coverAssetId: null, featuredRank: 10, status: 'DRAFT', publishedAt: null, updatedAt: '2026-08-19T01:00:00Z', tagCodes: ['promotion'] }])
    adminApi.changeAdminTemplateStatus.mockResolvedValue({ id: 1001, name: '朋友圈促销', width: 1080, height: 1440, categoryCode: 'marketing', coverAssetId: null, featuredRank: 10, status: 'PUBLISHED', publishedAt: '2026-08-19T01:00:00Z', updatedAt: '2026-08-19T01:00:00Z', tagCodes: ['promotion'] })
    const session = useSessionStore()
    session.setSession({ accessToken: 'access-token', tokenType: 'Bearer', expiresIn: 900, userId: 7, tenantId: 11, tenantRole: 'ADMIN' }, '13800000000')

    render(AdminTemplatesPage, { global: { plugins: [pinia] } })

    expect(await screen.findByText('朋友圈促销')).toBeVisible()
    const filter = screen.getByRole('combobox', { name: '按状态筛选' })
    await fireEvent.update(filter, 'DRAFT')
    await waitFor(() => expect(adminApi.loadAdminTemplates).toHaveBeenLastCalledWith('DRAFT', 'access-token'))

    const status = await screen.findByRole('combobox', { name: '修改 朋友圈促销 的状态' })
    await fireEvent.update(status, 'PUBLISHED')
    await waitFor(() => expect(adminApi.changeAdminTemplateStatus).toHaveBeenCalledWith(1001, 'PUBLISHED', 'access-token'))
    expect(status).toHaveValue('PUBLISHED')
  })

  it('keeps template mutations read-only for an operator', async () => {
    adminApi.loadAdminTemplates.mockResolvedValue([{ id: 1001, name: '朋友圈促销', width: 1080, height: 1440, categoryCode: 'marketing', coverAssetId: null, featuredRank: 10, status: 'PUBLISHED', publishedAt: '2026-08-19T01:00:00Z', updatedAt: '2026-08-19T01:00:00Z', tagCodes: ['promotion'] }])
    const session = useSessionStore()
    session.setSession({ accessToken: 'access-token', tokenType: 'Bearer', expiresIn: 900, userId: 7, tenantId: 11, tenantRole: 'OPERATOR' }, '13800000000')

    render(AdminTemplatesPage, { global: { plugins: [pinia] } })

    expect(await screen.findByRole('combobox', { name: '修改 朋友圈促销 的状态' })).toBeDisabled()
    expect(screen.queryByRole('button', { name: '新增模板' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '编辑 朋友圈促销' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '删除 朋友圈促销' })).not.toBeInTheDocument()
  })

  it('creates and edits a template with category and tags for an administrator', async () => {
    adminApi.loadAdminTemplates.mockResolvedValue([])
    adminApi.loadAdminTemplateCategories.mockResolvedValue([{ id: 10, code: 'marketing', name: '营销推广', parentCode: null, sortOrder: 10, status: 'PUBLISHED' }])
    adminApi.loadAdminTemplateTags.mockResolvedValue([{ id: 20, code: 'promotion', name: '促销', sortOrder: 10, status: 'PUBLISHED' }, { id: 21, code: 'seasonal', name: '季节', sortOrder: 20, status: 'PUBLISHED' }])
    adminApi.createAdminTemplate.mockResolvedValue({ id: 1002, name: '节日促销', width: 1080, height: 1440, categoryCode: 'marketing', coverAssetId: null, featuredRank: 20, status: 'DRAFT', publishedAt: null, updatedAt: '2026-08-19T01:00:00Z', tagCodes: ['promotion'] })
    adminApi.updateAdminTemplate.mockResolvedValue({ id: 1002, name: '节日活动', width: 1200, height: 1600, categoryCode: 'marketing', coverAssetId: null, featuredRank: 30, status: 'DRAFT', publishedAt: null, updatedAt: '2026-08-19T01:00:00Z', tagCodes: ['seasonal'] })
    const session = useSessionStore()
    session.setSession({ accessToken: 'access-token', tokenType: 'Bearer', expiresIn: 900, userId: 7, tenantId: 11, tenantRole: 'ADMIN' }, '13800000000')

    render(AdminTemplatesPage, { global: { plugins: [pinia] } })
    await fireEvent.click(await screen.findByRole('button', { name: '新增模板' }))
    await fireEvent.update(screen.getByRole('textbox', { name: '模板名称' }), '节日促销')
    await fireEvent.update(screen.getByRole('spinbutton', { name: '模板宽度' }), '1080')
    await fireEvent.update(screen.getByRole('spinbutton', { name: '模板高度' }), '1440')
    await fireEvent.update(screen.getByRole('combobox', { name: '模板分类' }), 'marketing')
    await fireEvent.click(screen.getByRole('checkbox', { name: '标签 促销' }))
    await fireEvent.update(screen.getByRole('spinbutton', { name: '模板推荐位' }), '20')
    await fireEvent.click(screen.getByRole('button', { name: '保存' }))
    await waitFor(() => expect(adminApi.createAdminTemplate).toHaveBeenCalledWith({ name: '节日促销', width: 1080, height: 1440, categoryCode: 'marketing', tagCodes: ['promotion'], featuredRank: 20 }, 'access-token'))
    await fireEvent.click(screen.getByRole('button', { name: '编辑 节日促销' }))
    await fireEvent.update(screen.getByRole('textbox', { name: '模板名称' }), '节日活动')
    await fireEvent.update(screen.getByRole('spinbutton', { name: '模板宽度' }), '1200')
    await fireEvent.update(screen.getByRole('spinbutton', { name: '模板高度' }), '1600')
    await fireEvent.click(screen.getByRole('checkbox', { name: '标签 促销' }))
    await fireEvent.click(screen.getByRole('checkbox', { name: '标签 季节' }))
    await fireEvent.update(screen.getByRole('spinbutton', { name: '模板推荐位' }), '30')
    await fireEvent.click(screen.getByRole('button', { name: '保存' }))
    await waitFor(() => expect(adminApi.updateAdminTemplate).toHaveBeenCalledWith(1002, { name: '节日活动', width: 1200, height: 1600, categoryCode: 'marketing', tagCodes: ['seasonal'], featuredRank: 30 }, 'access-token'))
  })

  it('binds a published cover and lets an administrator clear it from the template form', async () => {
    adminApi.loadAdminTemplates.mockResolvedValue([{ id: 1001, name: '朋友圈促销', width: 1080, height: 1440, categoryCode: null, coverAssetId: null, featuredRank: null, status: 'DRAFT', publishedAt: null, updatedAt: null, tagCodes: [] }])
    adminApi.loadAdminTemplateCategories.mockResolvedValue([])
    adminApi.loadAdminTemplateTags.mockResolvedValue([])
    adminApi.loadAdminTemplateCoverAssets.mockResolvedValue([{ id: 50, objectKey: 'platform/template-cover/a.png', mimeType: 'image/png', fileSize: 4, sha256: 'a'.repeat(64), width: 1080, height: 1440, status: 'PUBLISHED', createdAt: null, updatedAt: null }])
    adminApi.updateAdminTemplate.mockResolvedValue({ id: 1001, name: '朋友圈促销', width: 1080, height: 1440, categoryCode: null, coverAssetId: null, featuredRank: null, status: 'DRAFT', publishedAt: null, updatedAt: null, tagCodes: [] })
    const session = useSessionStore()
    session.setSession({ accessToken: 'access-token', tokenType: 'Bearer', expiresIn: 900, userId: 7, tenantId: 11, tenantRole: 'ADMIN' }, '13800000000')
    render(AdminTemplatesPage, { global: { plugins: [pinia] } })

    await fireEvent.click(await screen.findByRole('button', { name: '编辑 朋友圈促销' }))
    await fireEvent.update(screen.getByRole('combobox', { name: '模板封面' }), '50')
    await fireEvent.click(screen.getByRole('button', { name: '保存' }))
    await waitFor(() => expect(adminApi.bindAdminTemplateCover).toHaveBeenCalledWith(1001, 50, 'access-token'))
  })

  it('strictly deletes an unreferenced template and keeps a referenced one', async () => {
    adminApi.loadAdminTemplates.mockResolvedValue([
      { id: 1001, name: '旧模板', width: 1080, height: 1440, categoryCode: 'marketing', coverAssetId: null, featuredRank: null, status: 'DRAFT', publishedAt: null, updatedAt: null, tagCodes: [] },
      { id: 1002, name: '活动模板', width: 1080, height: 1440, categoryCode: 'marketing', coverAssetId: null, featuredRank: null, status: 'PUBLISHED', publishedAt: null, updatedAt: null, tagCodes: ['promotion'] },
    ])
    adminApi.deleteAdminTemplate.mockResolvedValueOnce(null).mockRejectedValueOnce(new ApiError(409, 'TEMPLATE_IN_USE', '模板仍被使用，无法删除'))
    const session = useSessionStore()
    session.setSession({ accessToken: 'access-token', tokenType: 'Bearer', expiresIn: 900, userId: 7, tenantId: 11, tenantRole: 'ADMIN' }, '13800000000')
    render(AdminTemplatesPage, { global: { plugins: [pinia] } })

    await fireEvent.click(await screen.findByRole('button', { name: '删除 旧模板' }))
    await fireEvent.click(screen.getByRole('button', { name: '确认删除' }))
    await waitFor(() => expect(adminApi.deleteAdminTemplate).toHaveBeenCalledWith(1001, 'access-token'))
    expect(screen.queryByText('旧模板')).not.toBeInTheDocument()
    await fireEvent.click(screen.getByRole('button', { name: '删除 活动模板' }))
    await fireEvent.click(screen.getByRole('button', { name: '确认删除' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('模板仍被使用')
    expect(screen.getByText('活动模板')).toBeVisible()
  })
})
