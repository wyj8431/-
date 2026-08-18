import { request } from './http'
import type { TemplateCategory, TemplateDetail, TemplatePage } from './types'

export interface TemplateSearchParams {
  keyword?: string
  categoryCode?: string
  tagCode?: string
  page: number
  pageSize: number
}

export function searchTemplates(params: TemplateSearchParams, signal?: AbortSignal) {
  const query = new URLSearchParams()
  if (params.keyword) query.set('keyword', params.keyword)
  if (params.categoryCode) query.set('categoryCode', params.categoryCode)
  if (params.tagCode) query.set('tagCode', params.tagCode)
  query.set('page', String(params.page))
  query.set('pageSize', String(params.pageSize))
  return request<TemplatePage>(`/api/v1/templates?${query.toString()}`, { signal })
}

export function loadTemplateCategories() {
  return request<TemplateCategory[]>('/api/v1/template-categories')
}

export function loadTemplate(templateId: number) {
  return request<TemplateDetail>(`/api/v1/templates/${templateId}`)
}
