import { download, request } from './http'
import type {
  AdminAuditLogPage,
  AdminSummary,
  TenantMemberPage,
  TenantRole,
  TenantRoleChangeResult,
  AdminTemplateCategory,
  TemplateCategoryStatus,
  AdminTemplate,
  CreateAdminTemplateInput,
  UpdateAdminTemplateInput,
  TemplateAdminStatus,
  AdminTemplateTag,
  TemplateTagStatus,
  CreateAdminTemplateTagInput,
  UpdateAdminTemplateTagInput,
} from './types'

export function loadAdminSummary(accessToken?: string | null) {
  return request<AdminSummary>('/api/v1/admin/summary', {}, accessToken)
}

export interface TenantMemberQuery {
  page?: number
  pageSize?: number
  role?: TenantRole
  status?: 'ACTIVE' | 'DISABLED'
}

export function loadTenantMembers(
  params: TenantMemberQuery = {},
  accessToken?: string | null,
) {
  const query = new URLSearchParams()
  query.set('page', String(params.page ?? 1))
  query.set('pageSize', String(params.pageSize ?? 20))
  if (params.role) query.set('role', params.role)
  if (params.status) query.set('status', params.status)
  return request<TenantMemberPage>(`/api/v1/admin/users?${query.toString()}`, {}, accessToken)
}

export function changeTenantRole(
  userId: number,
  tenantRole: TenantRole,
  accessToken?: string | null,
) {
  return request<TenantRoleChangeResult>(
    `/api/v1/admin/users/${userId}/tenant-role`,
    { method: 'PATCH', body: JSON.stringify({ tenantRole }) },
    accessToken,
  )
}

export interface AdminAuditLogQuery {
  page?: number
  pageSize?: number
  action?: string
  outcome?: 'SUCCESS' | 'FAILURE'
  from?: string
  to?: string
}

export function loadAdminAuditLogs(
  params: AdminAuditLogQuery = {},
  accessToken?: string | null,
) {
  const query = new URLSearchParams()
  query.set('page', String(params.page ?? 1))
  query.set('pageSize', String(params.pageSize ?? 20))
  if (params.action) query.set('action', params.action)
  if (params.outcome) query.set('outcome', params.outcome)
  if (params.from) query.set('from', params.from)
  if (params.to) query.set('to', params.to)
  return request<AdminAuditLogPage>(`/api/v1/admin/audit-logs?${query.toString()}`, {}, accessToken)
}

export function downloadAdminAuditLogs(
  params: Omit<AdminAuditLogQuery, 'page' | 'pageSize'> = {},
  accessToken?: string | null,
) {
  const query = new URLSearchParams()
  if (params.action) query.set('action', params.action)
  if (params.outcome) query.set('outcome', params.outcome)
  if (params.from) query.set('from', params.from)
  if (params.to) query.set('to', params.to)
  const suffix = query.size > 0 ? `?${query.toString()}` : ''
  return download(`/api/v1/admin/audit-logs/export${suffix}`, {}, accessToken)
}

export function loadAdminTemplateCategories(
  status?: TemplateCategoryStatus,
  accessToken?: string | null,
) {
  const query = status ? `?status=${encodeURIComponent(status)}` : ''
  return request<AdminTemplateCategory[]>(`/api/v1/admin/template-categories${query}`, {}, accessToken)
}

export function changeAdminTemplateCategoryStatus(
  code: string,
  status: TemplateCategoryStatus,
  accessToken?: string | null,
) {
  return request<AdminTemplateCategory>(
    `/api/v1/admin/template-categories/${encodeURIComponent(code)}/status`,
    { method: 'PATCH', body: JSON.stringify({ status }) },
    accessToken,
  )
}

export function loadAdminTemplates(
  status?: TemplateAdminStatus,
  accessToken?: string | null,
) {
  const query = status ? `?status=${encodeURIComponent(status)}` : ''
  return request<AdminTemplate[]>(`/api/v1/admin/templates${query}`, {}, accessToken)
}

export function changeAdminTemplateStatus(
  templateId: number,
  status: TemplateAdminStatus,
  accessToken?: string | null,
) {
  return request<AdminTemplate>(
    `/api/v1/admin/templates/${templateId}/status`,
    { method: 'PATCH', body: JSON.stringify({ status }) },
    accessToken,
  )
}

export function createAdminTemplate(input: CreateAdminTemplateInput, accessToken?: string | null) {
  return request<AdminTemplate>(
    '/api/v1/admin/templates',
    { method: 'POST', body: JSON.stringify(input) },
    accessToken,
  )
}

export function updateAdminTemplate(id: number, input: UpdateAdminTemplateInput, accessToken?: string | null) {
  return request<AdminTemplate>(
    `/api/v1/admin/templates/${id}`,
    { method: 'PATCH', body: JSON.stringify(input) },
    accessToken,
  )
}

export function deleteAdminTemplate(id: number, accessToken?: string | null) {
  return request<null>(`/api/v1/admin/templates/${id}`, { method: 'DELETE' }, accessToken)
}

export function loadAdminTemplateTags(
  status?: TemplateTagStatus,
  accessToken?: string | null,
) {
  const query = status ? `?status=${encodeURIComponent(status)}` : ''
  return request<AdminTemplateTag[]>(`/api/v1/admin/template-tags${query}`, {}, accessToken)
}

export function changeAdminTemplateTagStatus(
  code: string,
  status: TemplateTagStatus,
  accessToken?: string | null,
) {
  return request<AdminTemplateTag>(
    `/api/v1/admin/template-tags/${encodeURIComponent(code)}/status`,
    { method: 'PATCH', body: JSON.stringify({ status }) },
    accessToken,
  )
}

export function createAdminTemplateTag(input: CreateAdminTemplateTagInput, accessToken?: string | null) {
  return request<AdminTemplateTag>(
    '/api/v1/admin/template-tags',
    { method: 'POST', body: JSON.stringify(input) },
    accessToken,
  )
}

export function updateAdminTemplateTag(code: string, input: UpdateAdminTemplateTagInput, accessToken?: string | null) {
  return request<AdminTemplateTag>(
    `/api/v1/admin/template-tags/${encodeURIComponent(code)}`,
    { method: 'PATCH', body: JSON.stringify(input) },
    accessToken,
  )
}

export function deleteAdminTemplateTag(code: string, accessToken?: string | null) {
  return request<null>(
    `/api/v1/admin/template-tags/${encodeURIComponent(code)}`,
    { method: 'DELETE' },
    accessToken,
  )
}
