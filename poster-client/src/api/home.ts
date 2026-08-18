import { request } from './http'
import type { HomePayload } from './types'

export function loadHome() {
  return request<HomePayload>('/api/v1/home')
}
