import { fireEvent, render, screen, waitFor } from '@testing-library/vue'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const adminApi = vi.hoisted(() => ({
  loadAdminTemplateTags: vi.fn(),
  changeAdminTemplateTagStatus: vi.fn(),
  createAdminTemplateTag: vi.fn(),
  updateAdminTemplateTag: vi.fn(),
  deleteAdminTemplateTag: vi.fn(),
}))
vi.mock('@/api/admin', () => adminApi)

import AdminTemplateTagsPage from '@/features/admin/AdminTemplateTagsPage.vue'
import { ApiError } from '@/api/http'
import { useSessionStore } from '@/stores/session'

describe('AdminTemplateTagsPage', () => {
  let pinia: ReturnType<typeof createPinia>

  beforeEach(() => {
    pinia = createPinia()
    setActivePinia(pinia)
    adminApi.loadAdminTemplateTags.mockReset()
    adminApi.changeAdminTemplateTagStatus.mockReset()
    adminApi.createAdminTemplateTag.mockReset()
    adminApi.updateAdminTemplateTag.mockReset()
    adminApi.deleteAdminTemplateTag.mockReset()
  })

  it('allows an administrator to filter and change a tag status', async () => {
    adminApi.loadAdminTemplateTags.mockResolvedValue([{ id: 20, code: 'promotion', name: '促销', sortOrder: 10, status: 'DRAFT' }])
    adminApi.changeAdminTemplateTagStatus.mockResolvedValue({ id: 20, code: 'promotion', name: '促销', sortOrder: 10, status: 'PUBLISHED' })
    const session = useSessionStore()
    session.setSession({ accessToken: 'access-token', tokenType: 'Bearer', expiresIn: 900, userId: 7, tenantId: 11, tenantRole: 'ADMIN' }, '13800000000')

    render(AdminTemplateTagsPage, { global: { plugins: [pinia] } })

    expect(await screen.findByText('促销')).toBeVisible()
    const filter = screen.getByRole('combobox', { name: '按状态筛选' })
    await fireEvent.update(filter, 'DRAFT')
    await waitFor(() => expect(adminApi.loadAdminTemplateTags).toHaveBeenLastCalledWith('DRAFT', 'access-token'))

    const status = screen.getByRole('combobox', { name: '修改 促销 的状态' })
    await fireEvent.update(status, 'PUBLISHED')
    await waitFor(() => expect(adminApi.changeAdminTemplateTagStatus).toHaveBeenCalledWith('promotion', 'PUBLISHED', 'access-token'))
    expect(status).toHaveValue('PUBLISHED')
  })

  it('disables tag commands while its status change is pending', async () => {
    adminApi.loadAdminTemplateTags.mockResolvedValue([{ id: 20, code: 'promotion', name: '促销', sortOrder: 10, status: 'DRAFT' }])
    let resolveStatusChange!: (value: { id: number; code: string; name: string; sortOrder: number; status: string }) => void
    adminApi.changeAdminTemplateTagStatus.mockReturnValue(new Promise((resolve) => { resolveStatusChange = resolve }))
    const session = useSessionStore()
    session.setSession({ accessToken: 'access-token', tokenType: 'Bearer', expiresIn: 900, userId: 7, tenantId: 11, tenantRole: 'ADMIN' }, '13800000000')

    render(AdminTemplateTagsPage, { global: { plugins: [pinia] } })

    await fireEvent.update(await screen.findByRole('combobox', { name: '修改 促销 的状态' }), 'PUBLISHED')
    await waitFor(() => expect(adminApi.changeAdminTemplateTagStatus).toHaveBeenCalledWith('promotion', 'PUBLISHED', 'access-token'))
    expect(screen.getByRole('button', { name: '编辑 促销' })).toBeDisabled()
    expect(screen.getByRole('button', { name: '删除 促销' })).toBeDisabled()

    resolveStatusChange({ id: 20, code: 'promotion', name: '促销', sortOrder: 10, status: 'PUBLISHED' })
    await waitFor(() => expect(screen.getByRole('button', { name: '编辑 促销' })).not.toBeDisabled())
  })

  it('keeps status control read-only for an operator', async () => {
    adminApi.loadAdminTemplateTags.mockResolvedValue([{ id: 20, code: 'promotion', name: '促销', sortOrder: 10, status: 'PUBLISHED' }])
    const session = useSessionStore()
    session.setSession({ accessToken: 'access-token', tokenType: 'Bearer', expiresIn: 900, userId: 7, tenantId: 11, tenantRole: 'OPERATOR' }, '13800000000')

    render(AdminTemplateTagsPage, { global: { plugins: [pinia] } })

    expect(await screen.findByRole('combobox', { name: '修改 促销 的状态' })).toBeDisabled()
  })

  it('creates and edits a tag for an administrator', async () => {
    adminApi.loadAdminTemplateTags.mockResolvedValue([])
    adminApi.createAdminTemplateTag.mockResolvedValue({ id: 21, code: 'holiday-sale', name: '节日促销', sortOrder: 20, status: 'DRAFT' })
    adminApi.updateAdminTemplateTag.mockResolvedValue({ id: 21, code: 'holiday-sale', name: '节日活动', sortOrder: 30, status: 'DRAFT' })
    const session = useSessionStore()
    session.setSession({ accessToken: 'access-token', tokenType: 'Bearer', expiresIn: 900, userId: 7, tenantId: 11, tenantRole: 'ADMIN' }, '13800000000')

    render(AdminTemplateTagsPage, { global: { plugins: [pinia] } })
    await fireEvent.click(await screen.findByRole('button', { name: '新增标签' }))
    await fireEvent.update(screen.getByRole('textbox', { name: '标签编码' }), 'holiday-sale')
    await fireEvent.update(screen.getByRole('textbox', { name: '标签名称' }), '节日促销')
    await fireEvent.update(screen.getByRole('spinbutton', { name: '标签排序' }), '20')
    await fireEvent.click(screen.getByRole('button', { name: '保存' }))
    await waitFor(() => expect(adminApi.createAdminTemplateTag).toHaveBeenCalledWith({ code: 'holiday-sale', name: '节日促销', sortOrder: 20 }, 'access-token'))
    expect(await screen.findByText('节日促销')).toBeVisible()

    await fireEvent.click(screen.getByRole('button', { name: '编辑 节日促销' }))
    await fireEvent.update(screen.getByRole('textbox', { name: '标签名称' }), '节日活动')
    await fireEvent.update(screen.getByRole('spinbutton', { name: '标签排序' }), '30')
    await fireEvent.click(screen.getByRole('button', { name: '保存' }))
    await waitFor(() => expect(adminApi.updateAdminTemplateTag).toHaveBeenCalledWith('holiday-sale', { name: '节日活动', sortOrder: 30 }, 'access-token'))
    expect(await screen.findByText('节日活动')).toBeVisible()
  })

  it('removes an unreferenced tag and keeps an in-use tag after conflict', async () => {
    adminApi.loadAdminTemplateTags.mockResolvedValue([
      { id: 20, code: 'promotion', name: '促销', sortOrder: 10, status: 'DRAFT' },
      { id: 21, code: 'campaign', name: '活动', sortOrder: 20, status: 'PUBLISHED' },
    ])
    adminApi.deleteAdminTemplateTag
      .mockResolvedValueOnce(null)
      .mockRejectedValueOnce(new ApiError(409, 'TEMPLATE_TAG_IN_USE', '标签仍被模板引用，无法删除'))
    const session = useSessionStore()
    session.setSession({ accessToken: 'access-token', tokenType: 'Bearer', expiresIn: 900, userId: 7, tenantId: 11, tenantRole: 'ADMIN' }, '13800000000')

    render(AdminTemplateTagsPage, { global: { plugins: [pinia] } })
    await fireEvent.click(await screen.findByRole('button', { name: '删除 促销' }))
    await fireEvent.click(screen.getByRole('button', { name: '确认删除' }))
    await waitFor(() => expect(adminApi.deleteAdminTemplateTag).toHaveBeenCalledWith('promotion', 'access-token'))
    expect(screen.queryByText('促销')).not.toBeInTheDocument()

    await fireEvent.click(screen.getByRole('button', { name: '删除 活动' }))
    await fireEvent.click(screen.getByRole('button', { name: '确认删除' }))
    await waitFor(() => expect(adminApi.deleteAdminTemplateTag).toHaveBeenCalledWith('campaign', 'access-token'))
    expect(await screen.findByRole('alert')).toHaveTextContent('标签仍被模板引用')
    expect(screen.getByText('活动')).toBeVisible()
  })

  it('does not render tag write commands for an operator', async () => {
    adminApi.loadAdminTemplateTags.mockResolvedValue([{ id: 20, code: 'promotion', name: '促销', sortOrder: 10, status: 'PUBLISHED' }])
    const session = useSessionStore()
    session.setSession({ accessToken: 'access-token', tokenType: 'Bearer', expiresIn: 900, userId: 7, tenantId: 11, tenantRole: 'OPERATOR' }, '13800000000')

    render(AdminTemplateTagsPage, { global: { plugins: [pinia] } })
    expect(await screen.findByText('促销')).toBeVisible()
    expect(screen.queryByRole('button', { name: '新增标签' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '编辑 促销' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '删除 促销' })).not.toBeInTheDocument()
  })
})
