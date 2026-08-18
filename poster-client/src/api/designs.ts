import { request } from './http'
import type { DesignView } from './types'

export function createDesign(templateId: number, name: string, accessToken: string) {
  return request<DesignView>('/api/v1/designs', {
    method: 'POST',
    body: JSON.stringify({ templateId, name }),
  }, accessToken)
}
