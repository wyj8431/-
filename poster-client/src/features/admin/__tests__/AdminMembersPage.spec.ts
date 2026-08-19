import { fireEvent, render, screen, waitFor } from '@testing-library/vue'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const adminApi = vi.hoisted(() => ({
  loadTenantMembers: vi.fn(),
  changeTenantRole: vi.fn(),
}))
vi.mock('@/api/admin', () => adminApi)

import AdminMembersPage from '@/features/admin/AdminMembersPage.vue'
import { useSessionStore } from '@/stores/session'

describe('AdminMembersPage', () => {
  let pinia: ReturnType<typeof createPinia>

  beforeEach(() => {
    pinia = createPinia()
    setActivePinia(pinia)
    adminApi.loadTenantMembers.mockReset()
    adminApi.changeTenantRole.mockReset()
  })

  it('loads filtered members and updates a role for an administrator', async () => {
    adminApi.loadTenantMembers.mockResolvedValue({
      items: [{ userId: 8, phoneMasked: '139****0008', tenantRole: 'USER', userStatus: 'ACTIVE', joinedAt: null }],
      page: 1,
      pageSize: 20,
      total: 1,
    })
    adminApi.changeTenantRole.mockResolvedValue({ userId: 8, tenantId: 11, tenantRole: 'OPERATOR' })
    const session = useSessionStore()
    session.setSession({ accessToken: 'access-token', tokenType: 'Bearer', expiresIn: 900, userId: 7, tenantId: 11, tenantRole: 'ADMIN' }, '13800000000')

    render(AdminMembersPage, { global: { plugins: [pinia] } })

    const roleFilter = await screen.findByRole('combobox', { name: '按角色筛选' })
    await fireEvent.update(roleFilter, 'OPERATOR')
    await fireEvent.click(screen.getByRole('button', { name: '应用筛选' }))
    await waitFor(() => expect(adminApi.loadTenantMembers).toHaveBeenLastCalledWith(
      { page: 1, pageSize: 20, role: 'OPERATOR', status: undefined },
      'access-token',
    ))

    const memberRole = screen.getByRole('combobox', { name: '修改 139****0008 的角色' })
    await fireEvent.update(memberRole, 'OPERATOR')
    await waitFor(() => expect(adminApi.changeTenantRole).toHaveBeenCalledWith(8, 'OPERATOR', 'access-token'))
    expect(memberRole).toHaveValue('OPERATOR')
  })

  it('does not load members for a normal user', async () => {
    const session = useSessionStore()
    session.setSession({ accessToken: 'access-token', tokenType: 'Bearer', expiresIn: 900, userId: 7, tenantId: 11, tenantRole: 'USER' }, '13800000000')

    render(AdminMembersPage, { global: { plugins: [pinia] } })

    expect(await screen.findByText('无权访问成员管理')).toBeVisible()
    expect(adminApi.loadTenantMembers).not.toHaveBeenCalled()
  })
})
