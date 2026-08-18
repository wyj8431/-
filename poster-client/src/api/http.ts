import type { ApiEnvelope } from './types'

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
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`)

  const response = await fetch(path, { ...init, headers })
  const payload = (await response.json().catch(() => null)) as ApiEnvelope<T> | null
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
