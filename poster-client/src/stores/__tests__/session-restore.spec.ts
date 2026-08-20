import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const authApi = vi.hoisted(() => ({
  refresh: vi.fn(),
  me: vi.fn(),
  logout: vi.fn(),
}))

vi.mock('@/api/auth', () => authApi)

import { useSessionStore } from '@/stores/session'

describe('session restore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    authApi.refresh.mockReset()
    authApi.me.mockReset()
    authApi.logout.mockReset()
  })

  it('deduplicates concurrent restore requests and hydrates the current identity', async () => {
    authApi.refresh.mockResolvedValue({ accessToken: 'restored-token', tokenType: 'Bearer', expiresIn: 900 })
    authApi.me.mockResolvedValue({ userId: 7, tenantId: 11, phone: '13800000000', tenantRole: 'ADMIN', wechatBound: false })
    const session = useSessionStore()

    const [first, second] = await Promise.all([session.restore(), session.restore()])

    expect(first).toBe(true)
    expect(second).toBe(true)
    expect(authApi.refresh).toHaveBeenCalledTimes(1)
    expect(authApi.me).toHaveBeenCalledWith('restored-token')
    expect(session.isAuthenticated).toBe(true)
    expect(session.phone).toBe('13800000000')
    expect(session.wechatBound).toBe(false)
  })

  it('completes local logout when server-side revocation is unavailable', async () => {
    authApi.logout.mockRejectedValue(new Error('Network unavailable'))
    const session = useSessionStore()
    session.setSession({
      accessToken: 'access-token', tokenType: 'Bearer', expiresIn: 900,
      userId: 7, tenantId: 11, tenantRole: 'USER',
    }, '13800000000')

    await expect(session.logout()).resolves.toBeUndefined()

    expect(session.isAuthenticated).toBe(false)
  })
})
