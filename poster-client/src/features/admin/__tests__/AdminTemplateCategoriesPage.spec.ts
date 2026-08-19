import { fireEvent, render, screen, waitFor } from '@testing-library/vue'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const adminApi = vi.hoisted(() => ({
  loadAdminTemplateCategories: vi.fn(),
  changeAdminTemplateCategoryStatus: vi.fn(),
}))
vi.mock('@/api/admin', () => adminApi)

import AdminTemplateCategoriesPage from '@/features/admin/AdminTemplateCategoriesPage.vue'
import { useSessionStore } from '@/stores/session'

describe('AdminTemplateCategoriesPage', () => {
  let pinia: ReturnType<typeof createPinia>

  beforeEach(() => {
    pinia = createPinia()
    setActivePinia(pinia)
    adminApi.loadAdminTemplateCategories.mockReset()
    adminApi.changeAdminTemplateCategoryStatus.mockReset()
  })

  it('allows an administrator to filter and change a category status', async () => {
    adminApi.loadAdminTemplateCategories.mockResolvedValue([{ id: 10, code: 'marketing', name: '营销推广', parentCode: null, sortOrder: 10, status: 'DRAFT' }])
    adminApi.changeAdminTemplateCategoryStatus.mockResolvedValue({ id: 10, code: 'marketing', name: '营销推广', parentCode: null, sortOrder: 10, status: 'PUBLISHED' })
    const session = useSessionStore()
    session.setSession({ accessToken: 'access-token', tokenType: 'Bearer', expiresIn: 900, userId: 7, tenantId: 11, tenantRole: 'ADMIN' }, '13800000000')

    render(AdminTemplateCategoriesPage, { global: { plugins: [pinia] } })

    expect(await screen.findByText('营销推广')).toBeVisible()
    const filter = screen.getByRole('combobox', { name: '按状态筛选' })
    await fireEvent.update(filter, 'DRAFT')
    await waitFor(() => expect(adminApi.loadAdminTemplateCategories).toHaveBeenLastCalledWith('DRAFT', 'access-token'))

    const status = screen.getByRole('combobox', { name: '修改 营销推广 的状态' })
    await fireEvent.update(status, 'PUBLISHED')
    await waitFor(() => expect(adminApi.changeAdminTemplateCategoryStatus).toHaveBeenCalledWith('marketing', 'PUBLISHED', 'access-token'))
    expect(status).toHaveValue('PUBLISHED')
  })

  it('keeps status control read-only for an operator', async () => {
    adminApi.loadAdminTemplateCategories.mockResolvedValue([{ id: 10, code: 'marketing', name: '营销推广', parentCode: null, sortOrder: 10, status: 'PUBLISHED' }])
    const session = useSessionStore()
    session.setSession({ accessToken: 'access-token', tokenType: 'Bearer', expiresIn: 900, userId: 7, tenantId: 11, tenantRole: 'OPERATOR' }, '13800000000')

    render(AdminTemplateCategoriesPage, { global: { plugins: [pinia] } })

    expect(await screen.findByRole('combobox', { name: '修改 营销推广 的状态' })).toBeDisabled()
  })
})
