import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { LoginResult } from '@/api/types'

export interface PendingTemplateAction {
  templateId: number
  name: string
}

export const useSessionStore = defineStore('session', () => {
  const accessToken = ref<string | null>(null)
  const userId = ref<number | null>(null)
  const tenantId = ref<number | null>(null)
  const expiresAt = ref<number>(0)
  const pendingTemplateAction = ref<PendingTemplateAction | null>(null)

  const isAuthenticated = computed(() => Boolean(accessToken.value && expiresAt.value > Date.now()))

  function setSession(result: LoginResult) {
    accessToken.value = result.accessToken
    userId.value = result.userId
    tenantId.value = result.tenantId
    expiresAt.value = Date.now() + result.expiresIn * 1000
  }

  function setPendingTemplateAction(action: PendingTemplateAction) {
    pendingTemplateAction.value = action
  }

  async function resumePendingAction(handler: (action: PendingTemplateAction) => Promise<void>) {
    const action = pendingTemplateAction.value
    pendingTemplateAction.value = null
    if (action) await handler(action)
  }

  function clear() {
    accessToken.value = null
    userId.value = null
    tenantId.value = null
    expiresAt.value = 0
  }

  return {
    accessToken,
    userId,
    tenantId,
    expiresAt,
    pendingTemplateAction,
    isAuthenticated,
    setSession,
    setPendingTemplateAction,
    resumePendingAction,
    clear,
  }
})
