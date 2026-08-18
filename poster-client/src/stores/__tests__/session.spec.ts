import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import { useSessionStore } from '@/stores/session'

describe('session store', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('resumes one pending template creation after login and clears it', async () => {
    const session = useSessionStore()
    session.setPendingTemplateAction({ templateId: 1001, name: '朋友圈促销' })
    let createCount = 0

    await session.resumePendingAction(async (action) => {
      createCount += 1
      expect(action.templateId).toBe(1001)
    })
    await session.resumePendingAction(async () => {
      createCount += 1
    })

    expect(createCount).toBe(1)
    expect(session.pendingTemplateAction).toBeNull()
  })
})
