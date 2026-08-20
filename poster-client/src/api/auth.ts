import { request } from './http'
import type { CurrentIdentity, LoginResult, RefreshResult, WechatAuthorizeResult } from './types'

export function login(phone: string, verificationCode: string) {
  return request<LoginResult>('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ phone, verificationCode }),
  })
}

export function refresh() {
  return request<RefreshResult>('/api/v1/auth/refresh', { method: 'POST' })
}

export function me(accessToken?: string | null) {
  return request<CurrentIdentity>('/api/v1/auth/me', {}, accessToken)
}

export function logout() {
  return request<void>('/api/v1/auth/logout', { method: 'POST' })
}

export function beginWechatLogin(returnTo: string) {
  return request<WechatAuthorizeResult>('/api/v1/auth/wechat/login/authorize', {
    method: 'POST',
    body: JSON.stringify({ returnTo }),
  })
}

export function beginWechatBinding() {
  return request<WechatAuthorizeResult>('/api/v1/auth/wechat/bind/authorize', { method: 'POST' })
}
