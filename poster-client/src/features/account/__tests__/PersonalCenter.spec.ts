import { render, screen } from '@testing-library/vue'
import userEvent from '@testing-library/user-event'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import PersonalCenter from '@/features/account/PersonalCenter.vue'
import { useSessionStore } from '@/stores/session'

describe('PersonalCenter', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('shows the signed-in identity and clears it after logout confirmation', async () => {
    const session = useSessionStore()
    session.setSession({ accessToken: 'token', tokenType: 'Bearer', expiresIn: 900, userId: 7, tenantId: 11, tenantRole: 'ADMIN' }, '18212349611')
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: { template: '<div />' } }, { path: '/account', component: PersonalCenter }] })
    await router.push('/account')
    await router.isReady()

    render(PersonalCenter, { global: { plugins: [router] } })

    expect(screen.getAllByText('182****9611').length).toBeGreaterThan(0)
    await userEvent.click(screen.getByRole('button', { name: '退出登录' }))
    await userEvent.click(screen.getByRole('button', { name: '确定' }))

    expect(session.isAuthenticated).toBe(false)
  })
})
