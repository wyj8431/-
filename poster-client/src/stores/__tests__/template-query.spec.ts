import { createMemoryHistory, createRouter } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useTemplateQueryStore } from '@/stores/template-query'

describe('template-query store', () => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/', component: { template: '<div />' } }],
  })

  beforeEach(async () => {
    setActivePinia(createPinia())
    await router.push('/?keyword=%E4%BF%83%E9%94%80&page=2')
    await router.isReady()
  })

  it('hydrates keyword and resets page through URL state', async () => {
    const query = useTemplateQueryStore()
    query.bindRouter(router)
    expect(query.keyword).toBe('促销')
    expect(query.page).toBe(2)

    await query.setKeyword('餐饮')

    expect(router.currentRoute.value.query).toMatchObject({
      keyword: '餐饮',
      page: '1',
    })
  })

  it('debounces searches and aborts an obsolete request', async () => {
    vi.useFakeTimers()
    const query = useTemplateQueryStore()
    query.bindRouter(router)
    const search = vi.fn().mockResolvedValue({ items: [], page: 1, pageSize: 24, total: 0 })
    query.setSearchLoader(search)

    query.setKeyword('促销')
    query.setKeyword('餐饮')
    vi.advanceTimersByTime(349)
    expect(search).not.toHaveBeenCalled()
    vi.advanceTimersByTime(1)
    await vi.runOnlyPendingTimersAsync()

    expect(search).toHaveBeenCalledTimes(1)
    expect(search.mock.calls[0][0].keyword).toBe('餐饮')
    vi.useRealTimers()
  })
})
