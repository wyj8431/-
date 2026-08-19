import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, configureHttpSession, download, request } from '@/api/http'

describe('request', () => {
  afterEach(() => {
    configureHttpSession(null)
    vi.unstubAllGlobals()
  })

  it('refreshes once and retries one unauthorized authenticated request', async () => {
    const refresh = vi.fn().mockResolvedValue('fresh-access-token')
    configureHttpSession({ getAccessToken: () => 'expired-access-token', refresh, clear: vi.fn() })
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse(401, { code: 'UNAUTHORIZED', message: '登录已失效' }))
      .mockResolvedValueOnce(jsonResponse(200, { code: 'OK', message: 'success', data: { id: 7 } }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(request<{ id: number }>('/api/v1/designs/7')).resolves.toEqual({ id: 7 })

    expect(refresh).toHaveBeenCalledTimes(1)
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(new Headers(fetchMock.mock.calls[1][1].headers).get('Authorization')).toBe('Bearer fresh-access-token')
  })

  it('clears the session when the refresh request fails', async () => {
    const clear = vi.fn()
    configureHttpSession({
      getAccessToken: () => 'expired-access-token',
      refresh: vi.fn().mockRejectedValue(new Error('refresh failed')),
      clear,
    })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(401, { code: 'UNAUTHORIZED', message: '登录已失效' })))

    await expect(request('/api/v1/designs/7')).rejects.toBeInstanceOf(ApiError)
    expect(clear).toHaveBeenCalledTimes(1)
  })

  it('refreshes once and returns a Blob for an authenticated download', async () => {
    const refresh = vi.fn().mockResolvedValue('fresh-access-token')
    configureHttpSession({ getAccessToken: () => 'expired-access-token', refresh, clear: vi.fn() })
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse(401, { code: 'UNAUTHORIZED', message: '登录已失效' }))
      .mockResolvedValueOnce(new Response('id,action\r\n1,LOGIN\r\n', { status: 200, headers: { 'Content-Type': 'text/csv' } }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(download('/api/v1/admin/audit-logs/export')).resolves.toMatchObject({ type: 'text/csv', size: 20 })
    expect(refresh).toHaveBeenCalledTimes(1)
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(new Headers(fetchMock.mock.calls[1][1].headers).get('Authorization')).toBe('Bearer fresh-access-token')
  })
})

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}
