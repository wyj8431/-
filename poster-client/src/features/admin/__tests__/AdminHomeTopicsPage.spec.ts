import { fireEvent, render, screen, waitFor } from '@testing-library/vue'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const adminApi = vi.hoisted(() => ({
  loadAdminHomeTopics: vi.fn(),
  changeAdminHomeTopicStatus: vi.fn(),
  createAdminHomeTopic: vi.fn(),
  updateAdminHomeTopic: vi.fn(),
  deleteAdminHomeTopic: vi.fn(),
}))
vi.mock('@/api/admin', () => adminApi)

import AdminHomeTopicsPage from '@/features/admin/AdminHomeTopicsPage.vue'
import { useSessionStore } from '@/stores/session'

describe('AdminHomeTopicsPage', () => {
  let pinia: ReturnType<typeof createPinia>

  beforeEach(() => {
    pinia = createPinia()
    setActivePinia(pinia)
    Object.values(adminApi).forEach((mock) => mock.mockReset())
  })

  it('allows an administrator to filter and publish a topic', async () => {
    adminApi.loadAdminHomeTopics.mockResolvedValue([{ id: 30, code: 'summer-promotion', title: '夏日促销', subtitle: '精选模板', type: 'EDITORIAL_SCENE', coverAssetId: null, startsAt: null, endsAt: null, sortOrder: 10, status: 'DRAFT', templateIds: [1001] }])
    adminApi.changeAdminHomeTopicStatus.mockResolvedValue({ id: 30, code: 'summer-promotion', title: '夏日促销', subtitle: '精选模板', type: 'EDITORIAL_SCENE', coverAssetId: null, startsAt: null, endsAt: null, sortOrder: 10, status: 'PUBLISHED', templateIds: [1001] })
    const session = useSessionStore()
    session.setSession({ accessToken: 'access-token', tokenType: 'Bearer', expiresIn: 900, userId: 7, tenantId: 11, tenantRole: 'ADMIN' }, '13800000000')

    render(AdminHomeTopicsPage, { global: { plugins: [pinia] } })

    expect(await screen.findByText('夏日促销')).toBeVisible()
    await fireEvent.update(screen.getByRole('combobox', { name: '按状态筛选' }), 'DRAFT')
    await waitFor(() => expect(adminApi.loadAdminHomeTopics).toHaveBeenLastCalledWith('DRAFT', 'access-token'))
    await fireEvent.update(screen.getByRole('combobox', { name: '修改 夏日促销 的状态' }), 'PUBLISHED')
    await waitFor(() => expect(adminApi.changeAdminHomeTopicStatus).toHaveBeenCalledWith(30, 'PUBLISHED', 'access-token'))
  })

  it('keeps topic status read-only for an operator', async () => {
    adminApi.loadAdminHomeTopics.mockResolvedValue([{ id: 30, code: 'summer-promotion', title: '夏日促销', subtitle: null, type: 'HOTSPOT_CALENDAR', coverAssetId: null, startsAt: null, endsAt: null, sortOrder: 10, status: 'PUBLISHED', templateIds: [] }])
    const session = useSessionStore()
    session.setSession({ accessToken: 'access-token', tokenType: 'Bearer', expiresIn: 900, userId: 7, tenantId: 11, tenantRole: 'OPERATOR' }, '13800000000')

    render(AdminHomeTopicsPage, { global: { plugins: [pinia] } })
    expect(await screen.findByRole('combobox', { name: '修改 夏日促销 的状态' })).toBeDisabled()
  })
})
