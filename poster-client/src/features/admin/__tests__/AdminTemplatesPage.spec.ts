import { fireEvent, render, screen, waitFor } from '@testing-library/vue'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const adminApi = vi.hoisted(() => ({
  loadAdminTemplates: vi.fn(),
  changeAdminTemplateStatus: vi.fn(),
}))
vi.mock('@/api/admin', () => adminApi)

import AdminTemplatesPage from '@/features/admin/AdminTemplatesPage.vue'
import { useSessionStore } from '@/stores/session'

describe('AdminTemplatesPage', () => {
  let pinia: ReturnType<typeof createPinia>

  beforeEach(() => {
    pinia = createPinia()
    setActivePinia(pinia)
    adminApi.loadAdminTemplates.mockReset()
    adminApi.changeAdminTemplateStatus.mockReset()
  })

  it('allows an administrator to filter and change a template status', async () => {
    adminApi.loadAdminTemplates.mockResolvedValue([{ id: 1001, name: '朋友圈促销', width: 1080, height: 1440, categoryCode: 'marketing', coverAssetId: null, featuredRank: 10, status: 'DRAFT', publishedAt: null, updatedAt: '2026-08-19T01:00:00Z' }])
    adminApi.changeAdminTemplateStatus.mockResolvedValue({ id: 1001, name: '朋友圈促销', width: 1080, height: 1440, categoryCode: 'marketing', coverAssetId: null, featuredRank: 10, status: 'PUBLISHED', publishedAt: '2026-08-19T01:00:00Z', updatedAt: '2026-08-19T01:00:00Z' })
    const session = useSessionStore()
    session.setSession({ accessToken: 'access-token', tokenType: 'Bearer', expiresIn: 900, userId: 7, tenantId: 11, tenantRole: 'ADMIN' }, '13800000000')

    render(AdminTemplatesPage, { global: { plugins: [pinia] } })

    expect(await screen.findByText('朋友圈促销')).toBeVisible()
    const filter = screen.getByRole('combobox', { name: '按状态筛选' })
    await fireEvent.update(filter, 'DRAFT')
    await waitFor(() => expect(adminApi.loadAdminTemplates).toHaveBeenLastCalledWith('DRAFT', 'access-token'))

    const status = screen.getByRole('combobox', { name: '修改 朋友圈促销 的状态' })
    await fireEvent.update(status, 'PUBLISHED')
    await waitFor(() => expect(adminApi.changeAdminTemplateStatus).toHaveBeenCalledWith(1001, 'PUBLISHED', 'access-token'))
    expect(status).toHaveValue('PUBLISHED')
  })

  it('keeps status control read-only for an operator', async () => {
    adminApi.loadAdminTemplates.mockResolvedValue([{ id: 1001, name: '朋友圈促销', width: 1080, height: 1440, categoryCode: 'marketing', coverAssetId: null, featuredRank: 10, status: 'PUBLISHED', publishedAt: '2026-08-19T01:00:00Z', updatedAt: '2026-08-19T01:00:00Z' }])
    const session = useSessionStore()
    session.setSession({ accessToken: 'access-token', tokenType: 'Bearer', expiresIn: 900, userId: 7, tenantId: 11, tenantRole: 'OPERATOR' }, '13800000000')

    render(AdminTemplatesPage, { global: { plugins: [pinia] } })

    expect(await screen.findByRole('combobox', { name: '修改 朋友圈促销 的状态' })).toBeDisabled()
  })
})
