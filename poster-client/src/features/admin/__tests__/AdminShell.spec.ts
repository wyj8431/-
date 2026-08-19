import { fireEvent, render, screen, waitFor } from '@testing-library/vue'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const adminApi = vi.hoisted(() => ({
  loadAdminSummary: vi.fn(),
  loadTenantMembers: vi.fn(),
  loadAdminAuditLogs: vi.fn(),
  downloadAdminAuditLogs: vi.fn(),
  changeTenantRole: vi.fn(),
}))
vi.mock('@/api/admin', () => adminApi)

import AdminShell from '@/features/admin/AdminShell.vue'
import { useSessionStore } from '@/stores/session'

describe('AdminShell', () => {
  let pinia: ReturnType<typeof createPinia>

  beforeEach(() => {
    pinia = createPinia()
    setActivePinia(pinia)
    adminApi.loadAdminSummary.mockReset()
    adminApi.loadTenantMembers.mockReset()
    adminApi.loadAdminAuditLogs.mockReset()
    adminApi.loadAdminAuditLogs.mockResolvedValue({ items: [], page: 1, pageSize: 20, total: 0 })
    adminApi.downloadAdminAuditLogs.mockReset()
    adminApi.changeTenantRole.mockReset()
  })

  it('loads the team summary for an administrator', async () => {
    adminApi.loadAdminSummary.mockResolvedValue({
      memberCount: 8,
      activeUserCount: 7,
      auditCount: 14,
      health: 'UP',
    })
    adminApi.loadTenantMembers.mockResolvedValue({
      items: [{ userId: 8, phoneMasked: '139****0008', tenantRole: 'USER', userStatus: 'ACTIVE', joinedAt: '2026-08-18T09:00:00Z' }],
      page: 1,
      pageSize: 100,
      total: 1,
    })
    const session = useSessionStore()
    session.setSession({
      accessToken: 'access-token', tokenType: 'Bearer', expiresIn: 900,
      userId: 7, tenantId: 11, tenantRole: 'ADMIN',
    }, '13800000000')

    render(AdminShell, { global: { plugins: [pinia] } })

    expect(await screen.findByText('8')).toBeVisible()
    expect(await screen.findByText('139****0008')).toBeVisible()
    expect(screen.getByLabelText('修改 139****0008 的角色')).toHaveValue('USER')
    expect(screen.getAllByText('服务正常')).toHaveLength(2)
  })

  it('shows an in-page forbidden state for a normal user', async () => {
    const session = useSessionStore()
    session.setSession({
      accessToken: 'access-token', tokenType: 'Bearer', expiresIn: 900,
      userId: 7, tenantId: 11, tenantRole: 'USER',
    }, '13800000000')

    render(AdminShell, { global: { plugins: [pinia] } })

    expect(await screen.findByText('无权访问管理后台')).toBeVisible()
    expect(adminApi.loadAdminSummary).not.toHaveBeenCalled()
    expect(adminApi.loadTenantMembers).not.toHaveBeenCalled()
  })

  it('keeps member role controls read-only for an operator', async () => {
    adminApi.loadAdminSummary.mockResolvedValue({ memberCount: 1, activeUserCount: 1, auditCount: 0, health: 'UP' })
    adminApi.loadTenantMembers.mockResolvedValue({
      items: [{ userId: 8, phoneMasked: '139****0008', tenantRole: 'ADMIN', userStatus: 'ACTIVE', joinedAt: null }],
      page: 1,
      pageSize: 100,
      total: 1,
    })
    const session = useSessionStore()
    session.setSession({
      accessToken: 'access-token', tokenType: 'Bearer', expiresIn: 900,
      userId: 7, tenantId: 11, tenantRole: 'OPERATOR',
    }, '13800000000')

    render(AdminShell, { global: { plugins: [pinia] } })

    expect(await screen.findByRole('combobox', { name: '修改 139****0008 的角色' })).toBeDisabled()
  })

  it('updates a member role after an administrator command succeeds', async () => {
    adminApi.loadAdminSummary.mockResolvedValue({ memberCount: 1, activeUserCount: 1, auditCount: 0, health: 'UP' })
    adminApi.loadTenantMembers.mockResolvedValue({
      items: [{ userId: 8, phoneMasked: '139****0008', tenantRole: 'USER', userStatus: 'ACTIVE', joinedAt: null }],
      page: 1,
      pageSize: 100,
      total: 1,
    })
    adminApi.changeTenantRole.mockResolvedValue({ userId: 8, tenantId: 11, tenantRole: 'OPERATOR' })
    const session = useSessionStore()
    session.setSession({ accessToken: 'access-token', tokenType: 'Bearer', expiresIn: 900, userId: 7, tenantId: 11, tenantRole: 'ADMIN' }, '13800000000')

    render(AdminShell, { global: { plugins: [pinia] } })
    const select = await screen.findByRole('combobox', { name: '修改 139****0008 的角色' })
    await fireEvent.update(select, 'OPERATOR')

    await waitFor(() => expect(adminApi.changeTenantRole).toHaveBeenCalledWith(8, 'OPERATOR', 'access-token'))
    expect(select).toHaveValue('OPERATOR')
  })

  it('keeps the previous role when a role command fails', async () => {
    adminApi.loadAdminSummary.mockResolvedValue({ memberCount: 1, activeUserCount: 1, auditCount: 0, health: 'UP' })
    adminApi.loadTenantMembers.mockResolvedValue({
      items: [{ userId: 8, phoneMasked: '139****0008', tenantRole: 'USER', userStatus: 'ACTIVE', joinedAt: null }],
      page: 1,
      pageSize: 100,
      total: 1,
    })
    adminApi.changeTenantRole.mockRejectedValue(new Error('仅管理员可修改成员角色'))
    const session = useSessionStore()
    session.setSession({ accessToken: 'access-token', tokenType: 'Bearer', expiresIn: 900, userId: 7, tenantId: 11, tenantRole: 'ADMIN' }, '13800000000')

    render(AdminShell, { global: { plugins: [pinia] } })
    const select = await screen.findByRole('combobox', { name: '修改 139****0008 的角色' })
    await fireEvent.update(select, 'OPERATOR')

    expect(await screen.findByRole('alert')).toHaveTextContent('仅管理员可修改成员角色')
    expect(select).toHaveValue('USER')
  })

  it('exports the currently filtered audit logs', async () => {
    adminApi.loadAdminSummary.mockResolvedValue({ memberCount: 1, activeUserCount: 1, auditCount: 1, health: 'UP' })
    adminApi.loadTenantMembers.mockResolvedValue({ items: [], page: 1, pageSize: 100, total: 0 })
    adminApi.loadAdminAuditLogs.mockResolvedValue({ items: [], page: 1, pageSize: 20, total: 0 })
    adminApi.downloadAdminAuditLogs.mockResolvedValue(new Blob(['id,action\r\n'], { type: 'text/csv' }))
    const createObjectURL = vi.fn().mockReturnValue('blob:audit-export')
    const revokeObjectURL = vi.fn()
    vi.stubGlobal('URL', { createObjectURL, revokeObjectURL })
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)
    const session = useSessionStore()
    session.setSession({ accessToken: 'access-token', tokenType: 'Bearer', expiresIn: 900, userId: 7, tenantId: 11, tenantRole: 'ADMIN' }, '13800000000')

    render(AdminShell, { global: { plugins: [pinia] } })
    const action = await screen.findByRole('searchbox', { name: '审计动作筛选' })
    await fireEvent.update(action, 'LOGIN')
    await fireEvent.click(screen.getByRole('button', { name: '导出审计记录' }))

    await waitFor(() => expect(adminApi.downloadAdminAuditLogs).toHaveBeenCalledWith({ action: 'LOGIN', outcome: undefined, from: undefined, to: undefined }, 'access-token'))
    expect(createObjectURL).toHaveBeenCalledTimes(1)
    expect(click).toHaveBeenCalledTimes(1)
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:audit-export')
    click.mockRestore()
  })

  it('shows an inline error when audit export fails', async () => {
    adminApi.loadAdminSummary.mockResolvedValue({ memberCount: 1, activeUserCount: 1, auditCount: 0, health: 'UP' })
    adminApi.loadTenantMembers.mockResolvedValue({ items: [], page: 1, pageSize: 100, total: 0 })
    adminApi.downloadAdminAuditLogs.mockRejectedValue(new Error('导出失败'))
    const session = useSessionStore()
    session.setSession({ accessToken: 'access-token', tokenType: 'Bearer', expiresIn: 900, userId: 7, tenantId: 11, tenantRole: 'ADMIN' }, '13800000000')

    render(AdminShell, { global: { plugins: [pinia] } })
    await screen.findByText('暂无审计记录')
    await fireEvent.click(screen.getByRole('button', { name: '导出审计记录' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('导出失败')
  })
})
