import { render, screen } from '@testing-library/vue'
import userEvent from '@testing-library/user-event'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { describe, expect, it } from 'vitest'
import WorkbenchShell from '@/features/home/WorkbenchShell.vue'

describe('WorkbenchShell', () => {
  it('keeps later-phase tools non-navigable', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/', component: WorkbenchShell }],
    })
    await router.push('/')
    await router.isReady()
    render(WorkbenchShell, { global: { plugins: [createPinia(), router] } })

    await userEvent.click(screen.getByRole('button', { name: '智能创作' }))

    expect(router.currentRoute.value.path).toBe('/')
    expect(screen.getByText('将在后续阶段开放')).toBeVisible()
  })
})
