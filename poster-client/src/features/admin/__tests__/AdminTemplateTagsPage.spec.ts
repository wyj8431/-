import { fireEvent, render, screen, waitFor } from '@testing-library/vue'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const adminApi = vi.hoisted(() => ({
  loadAdminTemplateTags: vi.fn(),
  changeAdminTemplateTagStatus: vi.fn(),
}))
vi.mock('@/api/admin', () => adminApi)

import AdminTemplateTagsPage from '@/features/admin/AdminTemplateTagsPage.vue'
import { useSessionStore } from '@/stores/session'

describe('AdminTemplateTagsPage', () => {
  let pinia: ReturnType<typeof createPinia>

  beforeEach(() => {
    pinia = createPinia()
    setActivePinia(pinia)
    adminApi.loadAdminTemplateTags.mockReset()
    adminApi.changeAdminTemplateTagStatus.mockReset()
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

  it('keeps status control read-only for an operator', async () => {
    adminApi.loadAdminTemplateTags.mockResolvedValue([{ id: 20, code: 'promotion', name: '促销', sortOrder: 10, status: 'PUBLISHED' }])
    const session = useSessionStore()
    session.setSession({ accessToken: 'access-token', tokenType: 'Bearer', expiresIn: 900, userId: 7, tenantId: 11, tenantRole: 'OPERATOR' }, '13800000000')

    render(AdminTemplateTagsPage, { global: { plugins: [pinia] } })

    expect(await screen.findByRole('combobox', { name: '修改 促销 的状态' })).toBeDisabled()
  })
})
