import { render, screen, waitFor } from '@testing-library/vue'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import WechatAuthResultPage from '@/features/auth/WechatAuthResultPage.vue'
import { useSessionStore } from '@/stores/session'

describe('WechatAuthResultPage', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('restores a successful session and returns to the supplied in-site path', async () => {
    const session = useSessionStore()
    vi.spyOn(session, 'restore').mockResolvedValue(true)
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', component: { template: '<div>工作台</div>' } },
        { path: '/templates', component: { template: '<div>模板页</div>' } },
        { path: '/auth/wechat/result', component: WechatAuthResultPage },
      ],
    })
    await router.push({ path: '/auth/wechat/result', query: { result: 'success', returnTo: '/templates' } })
    await router.isReady()

    render(WechatAuthResultPage, { global: { plugins: [router] } })

    await waitFor(() => expect(session.restore).toHaveBeenCalledTimes(1))
    await waitFor(() => expect(router.currentRoute.value.fullPath).toBe('/templates'))
  })

  it('tells an unbound WeChat account how to proceed', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/auth/wechat/result', component: WechatAuthResultPage }],
    })
    await router.push({ path: '/auth/wechat/result', query: { result: 'unbound', returnTo: '/' } })
    await router.isReady()

    render(WechatAuthResultPage, { global: { plugins: [router] } })

    await waitFor(() => expect(screen.getByText('请先使用手机号登录，再绑定微信账号')).toBeVisible())
  })

  it('reports when the WeChat account belongs to a different platform account', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/auth/wechat/result', component: WechatAuthResultPage }],
    })
    await router.push({ path: '/auth/wechat/result', query: { result: 'already_bound', returnTo: '/' } })
    await router.isReady()

    render(WechatAuthResultPage, { global: { plugins: [router] } })

    await waitFor(() => expect(screen.getByText('该微信账号已绑定其他账户')).toBeVisible())
  })
})
