import type { ApiEnvelope } from './types'

export interface HttpSession {
  getAccessToken(): string | null
  refresh(): Promise<string | null>
  clear(): void
}

let session: HttpSession | null = null

export function configureHttpSession(nextSession: HttpSession | null) {
  session = nextSession
}

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly traceId?: string,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

export async function request<T>(
  path: string,
  init: RequestInit = {},
  accessToken?: string | null,
): Promise<T> {
  return performRequest(path, init, accessToken, false)
}

export async function download(
  path: string,
  init: RequestInit = {},
  accessToken?: string | null,
): Promise<Blob> {
  return performDownload(path, init, accessToken, false)
}

async function performRequest<T>(
  path: string,
  init: RequestInit,
  providedAccessToken: string | null | undefined,
  retried: boolean,
): Promise<T> {
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  const accessToken = providedAccessToken === undefined ? session?.getAccessToken() : providedAccessToken
  if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`)

  const response = await fetch(path, { ...init, headers, credentials: init.credentials ?? 'include' })
  const payload = (await response.json().catch(() => null)) as ApiEnvelope<T> | null
  if (response.status === 401 && !retried && canRefresh(path)) {
    try {
      const refreshedAccessToken = await session?.refresh()
      if (refreshedAccessToken) {
        return performRequest(path, init, refreshedAccessToken, true)
      }
    } catch {
      // The expired session is cleared below before returning the original API error.
    }
    session?.clear()
  }
  if (!response.ok || !payload || payload.code !== 'OK') {
    throw new ApiError(
      response.status,
      payload?.code ?? 'NETWORK_ERROR',
      payload?.message ?? '请求失败，请稍后重试',
      payload?.traceId,
    )
  }
  return payload.data
}

async function performDownload(
  path: string,
  init: RequestInit,
  providedAccessToken: string | null | undefined,
  retried: boolean,
): Promise<Blob> {
  const headers = new Headers(init.headers)
  headers.set('Accept', 'text/csv')
  const accessToken = providedAccessToken === undefined ? session?.getAccessToken() : providedAccessToken
  if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`)

  const response = await fetch(path, { ...init, headers, credentials: init.credentials ?? 'include' })
  if (response.status === 401 && !retried && canRefresh(path)) {
    try {
      const refreshedAccessToken = await session?.refresh()
      if (refreshedAccessToken) return performDownload(path, init, refreshedAccessToken, true)
    } catch {
      // The expired session is cleared below before returning the original API error.
    }
    session?.clear()
  }
  if (!response.ok) {
    const payload = (await response.json().catch(() => null)) as ApiEnvelope<unknown> | null
    throw new ApiError(
      response.status,
      payload?.code ?? 'NETWORK_ERROR',
      payload?.message ?? '下载失败，请稍后重试',
      payload?.traceId,
    )
  }
  return response.blob()
}

function canRefresh(path: string) {
  return !path.startsWith('/api/v1/auth/') && session !== null
}
