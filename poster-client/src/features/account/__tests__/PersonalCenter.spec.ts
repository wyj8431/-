import { render, screen, waitFor } from '@testing-library/vue'
import userEvent from '@testing-library/user-event'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import PersonalCenter from '@/features/account/PersonalCenter.vue'
import { useSessionStore } from '@/stores/session'

describe('PersonalCenter', () => {
  beforeEach(() => setActivePinia(createPinia()))
  afterEach(() => vi.unstubAllGlobals())

  it('shows the signed-in identity and clears it after logout confirmation', async () => {
    const session = useSessionStore()
    session.setSession({ accessToken: 'token', tokenType: 'Bearer', expiresIn: 900, userId: 7, tenantId: 11, tenantRole: 'ADMIN' }, '18212349611')
    session.wechatBound = false
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: { template: '<div />' } }, { path: '/account', component: PersonalCenter }] })
    await router.push('/account')
    await router.isReady()

    render(PersonalCenter, { global: { plugins: [router] } })

    expect(screen.getAllByText('182****9611').length).toBeGreaterThan(0)
    await userEvent.click(screen.getByRole('button', { name: '退出登录' }))
    await userEvent.click(screen.getByRole('button', { name: '确定' }))

    expect(session.isAuthenticated).toBe(false)
  })

  it('starts the protected WeChat binding flow only when the account is unbound', async () => {
    const session = useSessionStore()
    session.setSession({ accessToken: 'token', tokenType: 'Bearer', expiresIn: 900, userId: 7, tenantId: 11, tenantRole: 'ADMIN' }, '18212349611')
    session.wechatBound = false
    const assign = vi.fn()
    vi.stubGlobal('location', { assign })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      code: 'OK', message: 'success', data: { authorizeUrl: 'https://wechat.test/authorize?state=opaque' },
    }), { status: 200, headers: { 'Content-Type': 'application/json' } })))
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/', component: { template: '<div />' } }, { path: '/account', component: PersonalCenter }],
    })
    await router.push('/account')
    await router.isReady()

    render(PersonalCenter, { global: { plugins: [router] } })

    await userEvent.click(screen.getByRole('button', { name: '绑定微信账号' }))

    await waitFor(() => expect(assign).toHaveBeenCalledWith('https://wechat.test/authorize?state=opaque'))
  })
})
