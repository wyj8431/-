import { request } from './http'
import type { LoginResult } from './types'

export function login(phone: string, verificationCode: string) {
  return request<LoginResult>('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ phone, verificationCode }),
  })
}
