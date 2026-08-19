import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { logout as logoutRequest, me, refresh } from '@/api/auth'
import { configureHttpSession } from '@/api/http'
import type { CurrentIdentity, LoginResult, RefreshResult } from '@/api/types'

export interface PendingTemplateAction {
  templateId: number
  name: string
}

export const useSessionStore = defineStore('session', () => {
  const accessToken = ref<string | null>(null)
  const userId = ref<number | null>(null)
  const tenantId = ref<number | null>(null)
  const tenantRole = ref<LoginResult['tenantRole'] | null>(null)
  const phone = ref<string | null>(null)
  const expiresAt = ref<number>(0)
  const pendingTemplateAction = ref<PendingTemplateAction | null>(null)
  const restoring = ref(false)
  let refreshPromise: Promise<string | null> | null = null
  let restorePromise: Promise<boolean> | null = null

  const isAuthenticated = computed(() => Boolean(accessToken.value && expiresAt.value > Date.now()))

  const phoneMasked = computed(() => {
    if (!phone.value) return null
    return phone.value.replace(/^(\d{3})\d{4}(\d{4})$/, '$1****$2')
  })

  function setSession(result: LoginResult, accountPhone?: string) {
    accessToken.value = result.accessToken
    userId.value = result.userId
    tenantId.value = result.tenantId
    tenantRole.value = result.tenantRole
    if (accountPhone) phone.value = accountPhone.trim()
    expiresAt.value = Date.now() + result.expiresIn * 1000
  }

  function setAccessSession(result: RefreshResult) {
    accessToken.value = result.accessToken
    expiresAt.value = Date.now() + result.expiresIn * 1000
  }

  function setIdentity(identity: CurrentIdentity) {
    userId.value = identity.userId
    tenantId.value = identity.tenantId
    tenantRole.value = identity.tenantRole
    phone.value = identity.phone
  }

  function refreshAccessToken() {
    if (refreshPromise) return refreshPromise
    refreshPromise = refresh()
      .then((result) => {
        setAccessSession(result)
        return result.accessToken
      })
      .catch((error) => {
        clear({ preservePending: true })
        throw error
      })
      .finally(() => {
        refreshPromise = null
      })
    return refreshPromise
  }

  function restore() {
    if (isAuthenticated.value) return Promise.resolve(true)
    if (restorePromise) return restorePromise
    restoring.value = true
    restorePromise = refreshAccessToken()
      .then(async (token) => {
        if (!token) return false
        setIdentity(await me(token))
        return true
      })
      .catch(() => false)
      .finally(() => {
        restoring.value = false
        restorePromise = null
      })
    return restorePromise
  }

  function setPendingTemplateAction(action: PendingTemplateAction) {
    pendingTemplateAction.value = action
  }

  async function resumePendingAction(handler: (action: PendingTemplateAction) => Promise<void>) {
    const action = pendingTemplateAction.value
    pendingTemplateAction.value = null
    if (action) await handler(action)
  }

  function clear(options: { preservePending?: boolean } = {}) {
    accessToken.value = null
    userId.value = null
    tenantId.value = null
    tenantRole.value = null
    phone.value = null
    expiresAt.value = 0
    if (!options.preservePending) pendingTemplateAction.value = null
  }

  async function logout() {
    try {
      await logoutRequest()
    } catch {
      // A failed best-effort revocation must not keep the browser session signed in.
    } finally {
      clear()
    }
  }

  configureHttpSession({
    getAccessToken: () => accessToken.value,
    refresh: refreshAccessToken,
    clear: () => clear({ preservePending: true }),
  })

  return {
    accessToken,
    userId,
    tenantId,
    tenantRole,
    phone,
    phoneMasked,
    expiresAt,
    restoring,
    pendingTemplateAction,
    isAuthenticated,
    setSession,
    restore,
    refreshAccessToken,
    setPendingTemplateAction,
    resumePendingAction,
    clear,
    logout,
  }
})
