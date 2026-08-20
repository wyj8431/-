import { afterEach, describe, expect, it, vi } from 'vitest'
import { beginWechatBinding, beginWechatLogin } from '@/api/auth'

describe('WeChat auth API', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('requests a server-generated WeChat login URL', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ authorizeUrl: 'https://wechat.test/authorize?state=opaque' }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(beginWechatLogin('/templates')).resolves.toEqual({ authorizeUrl: 'https://wechat.test/authorize?state=opaque' })

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/auth/wechat/login/authorize', expect.objectContaining({
      method: 'POST',
      credentials: 'include',
      body: JSON.stringify({ returnTo: '/templates' }),
    }))
  })

  it('requests the protected server-generated binding URL', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ authorizeUrl: 'https://wechat.test/authorize?state=opaque' }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(beginWechatBinding()).resolves.toEqual({ authorizeUrl: 'https://wechat.test/authorize?state=opaque' })

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/auth/wechat/bind/authorize', expect.objectContaining({
      method: 'POST',
      credentials: 'include',
    }))
  })
})

function jsonResponse(data: unknown) {
  return new Response(JSON.stringify({ code: 'OK', message: 'success', data }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}
