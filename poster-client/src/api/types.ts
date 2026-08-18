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
