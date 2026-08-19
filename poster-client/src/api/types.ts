export interface ApiEnvelope<T> {
  code: string
  message: string
  data: T
  traceId?: string
}

export interface TemplateField {
  fieldKey: string
  label: string
  fieldType: string
  required: boolean
  defaultValue: string
}

export interface TemplateSummary {
  id: number
  name: string
  width: number
  height: number
  coverAssetId: number | null
  coverUrl: string | null
  categoryCode: string | null
  tagCodes: string[]
  publishedAt: string | null
}

export interface TemplateDetail extends TemplateSummary {
  schema: Record<string, unknown>
  fields: TemplateField[]
}

export interface TemplatePage {
  items: TemplateSummary[]
  page: number
  pageSize: number
  total: number
}

export interface TemplateCategory {
  code: string
  name: string
  parentCode?: string | null
}

export interface TemplateTag {
  code: string
  name: string
}

export interface HomePayload {
  trendingTags: TemplateTag[]
  featuredTemplates: TemplateSummary[]
  hotspotCalendar: Array<{
    code: string
    title: string
    startsAt: string | null
    templateCount: number
  }>
  editorialScenes: Array<{
    code: string
    title: string
    subtitle: string | null
    coverAssetId: number | null
    coverUrl: string | null
  }>
}

export interface LoginResult {
  accessToken: string
  tokenType: string
  expiresIn: number
  userId: number
  tenantId: number
  tenantRole: 'ADMIN' | 'USER' | 'OPERATOR'
}

export interface RefreshResult {
  accessToken: string
  tokenType: string
  expiresIn: number
}

export interface CurrentIdentity {
  userId: number
  tenantId: number
  phone: string
  tenantRole: LoginResult['tenantRole']
}

export interface AdminSummary {
  memberCount: number
  activeUserCount: number
  auditCount: number
  health: 'UP' | 'DOWN'
}

export type TenantRole = 'ADMIN' | 'USER' | 'OPERATOR'

export interface TenantMember {
  userId: number
  phoneMasked: string
  tenantRole: TenantRole
  userStatus: 'ACTIVE' | 'DISABLED'
  joinedAt: string | null
}

export interface TenantMemberPage {
  items: TenantMember[]
  page: number
  pageSize: number
  total: number
}

export interface TenantRoleChangeResult {
  userId: number
  tenantId: number
  tenantRole: TenantRole
}

export interface AdminAuditLog {
  id: number
  actorUserId: number | null
  actorPhoneMasked: string
  action: string
  resourceType: string
  resourceId: string | null
  outcome: 'SUCCESS' | 'FAILURE'
  requestId: string | null
  createdAt: string | null
}

export interface AdminAuditLogPage {
  items: AdminAuditLog[]
  page: number
  pageSize: number
  total: number
}

export type TemplateCategoryStatus = 'DRAFT' | 'PUBLISHED' | 'DISABLED'

export interface AdminTemplateCategory {
  id: number
  code: string
  name: string
  parentCode: string | null
  sortOrder: number
  status: TemplateCategoryStatus
}

export type TemplateTagStatus = 'DRAFT' | 'PUBLISHED' | 'DISABLED'

export interface AdminTemplateTag {
  id: number
  code: string
  name: string
  sortOrder: number
  status: TemplateTagStatus
}

export type TemplateAdminStatus = 'DRAFT' | 'PUBLISHED' | 'DISABLED'

export interface AdminTemplate {
  id: number
  name: string
  width: number
  height: number
  categoryCode: string | null
  coverAssetId: number | null
  featuredRank: number | null
  status: TemplateAdminStatus
  publishedAt: string | null
  updatedAt: string | null
}

export interface DesignView {
  id: number
  templateId: number
  name: string
  width: number
  height: number
  currentVersion: number
  schema: Record<string, unknown>
  updatedAt: string
}
