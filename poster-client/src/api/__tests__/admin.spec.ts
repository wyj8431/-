import { afterEach, describe, expect, it, vi } from 'vitest'
import { bindAdminTemplateCover, changeAdminTemplateCoverStatus, completeAdminTemplateCover, deleteAdminTemplateCover, loadAdminTemplateCoverAssets, presignAdminTemplateCover } from '@/api/admin'
import { changeAdminTemplateCategoryStatus, changeAdminTemplateStatus, changeAdminTemplateTagStatus, changeTenantRole, createAdminTemplateTag, createAdminTemplate, deleteAdminTemplateTag, deleteAdminTemplate, downloadAdminAuditLogs, loadAdminAuditLogs, loadAdminTemplateCategories, loadAdminTemplateTags, loadAdminTemplates, loadTenantMembers, updateAdminTemplateTag, updateAdminTemplate } from '@/api/admin'

describe('admin API', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('loads tenant members with typed paging and filters', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({
      code: 'OK',
      message: 'success',
      data: { items: [], page: 2, pageSize: 50, total: 0 },
    }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(loadTenantMembers({ page: 2, pageSize: 50, role: 'OPERATOR', status: 'ACTIVE' }, 'token'))
      .resolves.toEqual({ items: [], page: 2, pageSize: 50, total: 0 })

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/admin/users?page=2&pageSize=50&role=OPERATOR&status=ACTIVE',
      expect.objectContaining({ credentials: 'include' }),
    )
    expect(new Headers(fetchMock.mock.calls[0][1].headers).get('Authorization')).toBe('Bearer token')
  })

  it('sends an administrator-only role change command', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({
      code: 'OK',
      message: 'success',
      data: { userId: 8, tenantId: 11, tenantRole: 'ADMIN' },
    }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(changeTenantRole(8, 'ADMIN', 'token')).resolves.toEqual({ userId: 8, tenantId: 11, tenantRole: 'ADMIN' })

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/admin/users/8/tenant-role',
      expect.objectContaining({ method: 'PATCH', body: JSON.stringify({ tenantRole: 'ADMIN' }) }),
    )
  })

  it('loads tenant-scoped audit logs with outcome and time filters', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({
      code: 'OK',
      message: 'success',
      data: { items: [], page: 1, pageSize: 20, total: 0 },
    }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(loadAdminAuditLogs({
      page: 1,
      pageSize: 20,
      action: 'LOGIN',
      outcome: 'FAILURE',
      from: '2026-08-18T00:00:00Z',
      to: '2026-08-19T00:00:00Z',
    }, 'token')).resolves.toEqual({ items: [], page: 1, pageSize: 20, total: 0 })

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/admin/audit-logs?page=1&pageSize=20&action=LOGIN&outcome=FAILURE&from=2026-08-18T00%3A00%3A00Z&to=2026-08-19T00%3A00%3A00Z',
      expect.objectContaining({ credentials: 'include' }),
    )
  })

  it('loads and updates template category operations', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ code: 'OK', message: 'success', data: [] }))
      .mockResolvedValueOnce(jsonResponse({ code: 'OK', message: 'success', data: { id: 10, code: 'marketing', name: '营销推广', parentCode: null, sortOrder: 10, status: 'PUBLISHED' } }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(loadAdminTemplateCategories('DRAFT', 'token')).resolves.toEqual([])
    await expect(changeAdminTemplateCategoryStatus('marketing', 'PUBLISHED', 'token')).resolves.toMatchObject({ code: 'marketing', status: 'PUBLISHED' })

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/admin/template-categories?status=DRAFT', expect.objectContaining({ credentials: 'include' }))
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/admin/template-categories/marketing/status', expect.objectContaining({ method: 'PATCH', body: JSON.stringify({ status: 'PUBLISHED' }) }))
  })

  it('loads and updates template operations', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ code: 'OK', message: 'success', data: [] }))
      .mockResolvedValueOnce(jsonResponse({ code: 'OK', message: 'success', data: { id: 1001, name: '朋友圈促销', width: 1080, height: 1440, categoryCode: 'marketing', coverAssetId: null, featuredRank: 10, status: 'PUBLISHED', publishedAt: '2026-08-19T01:00:00Z', updatedAt: '2026-08-19T01:00:00Z' } }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(loadAdminTemplates('DRAFT', 'token')).resolves.toEqual([])
    await expect(changeAdminTemplateStatus(1001, 'PUBLISHED', 'token')).resolves.toMatchObject({ id: 1001, status: 'PUBLISHED' })

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/admin/templates?status=DRAFT', expect.objectContaining({ credentials: 'include' }))
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/admin/templates/1001/status', expect.objectContaining({ method: 'PATCH', body: JSON.stringify({ status: 'PUBLISHED' }) }))
  })

  it('creates, updates, and deletes templates with tag associations', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ code: 'OK', message: 'success', data: { id: 1002, name: '节日促销', width: 1080, height: 1440, categoryCode: 'marketing', coverAssetId: null, featuredRank: 20, status: 'DRAFT', publishedAt: null, updatedAt: '2026-08-19T01:00:00Z', tagCodes: ['promotion'] } }))
      .mockResolvedValueOnce(jsonResponse({ code: 'OK', message: 'success', data: { id: 1002, name: '节日活动', width: 1200, height: 1600, categoryCode: 'marketing', coverAssetId: null, featuredRank: 30, status: 'DRAFT', publishedAt: null, updatedAt: '2026-08-19T01:00:00Z', tagCodes: ['seasonal'] } }))
      .mockResolvedValueOnce(jsonResponse({ code: 'OK', message: 'success', data: null }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(createAdminTemplate({ name: '节日促销', width: 1080, height: 1440, categoryCode: 'marketing', tagCodes: ['promotion'], featuredRank: 20 }, 'token')).resolves.toMatchObject({ id: 1002, tagCodes: ['promotion'] })
    await expect(updateAdminTemplate(1002, { name: '节日活动', width: 1200, height: 1600, categoryCode: 'marketing', tagCodes: ['seasonal'], featuredRank: 30 }, 'token')).resolves.toMatchObject({ name: '节日活动', tagCodes: ['seasonal'] })
    await expect(deleteAdminTemplate(1002, 'token')).resolves.toBeNull()

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/admin/templates', expect.objectContaining({ method: 'POST', body: JSON.stringify({ name: '节日促销', width: 1080, height: 1440, categoryCode: 'marketing', tagCodes: ['promotion'], featuredRank: 20 }) }))
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/admin/templates/1002', expect.objectContaining({ method: 'PATCH', body: JSON.stringify({ name: '节日活动', width: 1200, height: 1600, categoryCode: 'marketing', tagCodes: ['seasonal'], featuredRank: 30 }) }))
    expect(fetchMock).toHaveBeenNthCalledWith(3, '/api/v1/admin/templates/1002', expect.objectContaining({ method: 'DELETE' }))
  })

  it('loads and updates template tag operations', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ code: 'OK', message: 'success', data: [] }))
      .mockResolvedValueOnce(jsonResponse({ code: 'OK', message: 'success', data: { id: 20, code: 'promotion', name: '促销', sortOrder: 10, status: 'PUBLISHED' } }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(loadAdminTemplateTags('DRAFT', 'token')).resolves.toEqual([])
    await expect(changeAdminTemplateTagStatus('promotion', 'PUBLISHED', 'token')).resolves.toMatchObject({ code: 'promotion', status: 'PUBLISHED' })

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/admin/template-tags?status=DRAFT', expect.objectContaining({ credentials: 'include' }))
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/admin/template-tags/promotion/status', expect.objectContaining({ method: 'PATCH', body: JSON.stringify({ status: 'PUBLISHED' }) }))
  })

  it('creates, updates, and deletes template tags with typed commands', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ code: 'OK', message: 'success', data: { id: 21, code: 'holiday-sale', name: '节日促销', sortOrder: 20, status: 'DRAFT' } }))
      .mockResolvedValueOnce(jsonResponse({ code: 'OK', message: 'success', data: { id: 21, code: 'holiday-sale', name: '节日活动', sortOrder: 30, status: 'DRAFT' } }))
      .mockResolvedValueOnce(jsonResponse({ code: 'OK', message: 'success', data: null }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(createAdminTemplateTag({ code: 'holiday-sale', name: '节日促销', sortOrder: 20 }, 'token')).resolves.toMatchObject({ code: 'holiday-sale', status: 'DRAFT' })
    await expect(updateAdminTemplateTag('holiday-sale', { name: '节日活动', sortOrder: 30 }, 'token')).resolves.toMatchObject({ name: '节日活动', sortOrder: 30 })
    await expect(deleteAdminTemplateTag('holiday-sale', 'token')).resolves.toBeNull()

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/admin/template-tags', expect.objectContaining({ method: 'POST', body: JSON.stringify({ code: 'holiday-sale', name: '节日促销', sortOrder: 20 }) }))
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/admin/template-tags/holiday-sale', expect.objectContaining({ method: 'PATCH', body: JSON.stringify({ name: '节日活动', sortOrder: 30 }) }))
    expect(fetchMock).toHaveBeenNthCalledWith(3, '/api/v1/admin/template-tags/holiday-sale', expect.objectContaining({ method: 'DELETE' }))
  })

  it('downloads filtered audit logs as a CSV Blob', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response('id,action\r\n', { status: 200, headers: { 'Content-Type': 'text/csv' } }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(downloadAdminAuditLogs({ action: 'LOGIN', outcome: 'FAILURE', from: '2026-08-18T00:00:00Z', to: '2026-08-19T00:00:00Z' }, 'token'))
      .resolves.toMatchObject({ type: 'text/csv', size: 11 })

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/admin/audit-logs/export?action=LOGIN&outcome=FAILURE&from=2026-08-18T00%3A00%3A00Z&to=2026-08-19T00%3A00%3A00Z',
      expect.objectContaining({ credentials: 'include' }),
    )
  })
  it('manages template cover assets and binds them to templates', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ code: 'OK', message: 'success', data: { sessionId: 9, objectKey: 'platform/template-cover/upload/a.png', uploadUrl: 'https://upload.test', expiresAt: '2026-08-19T01:00:00Z' } }))
      .mockResolvedValueOnce(jsonResponse({ code: 'OK', message: 'success', data: { id: 10, status: 'DRAFT' } }))
      .mockResolvedValueOnce(jsonResponse({ code: 'OK', message: 'success', data: [] }))
      .mockResolvedValueOnce(jsonResponse({ code: 'OK', message: 'success', data: { id: 10, status: 'PUBLISHED' } }))
      .mockResolvedValueOnce(jsonResponse({ code: 'OK', message: 'success', data: null }))
      .mockResolvedValueOnce(jsonResponse({ code: 'OK', message: 'success', data: { templateId: 1001, coverAssetId: 10 } }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(presignAdminTemplateCover({ fileName: 'cover.png', mimeType: 'image/png', fileSize: 4, sha256: 'a'.repeat(64) }, 'token')).resolves.toMatchObject({ sessionId: 9 })
    await expect(completeAdminTemplateCover(9, 'token')).resolves.toMatchObject({ id: 10 })
    await expect(loadAdminTemplateCoverAssets(undefined, 'token')).resolves.toEqual([])
    await expect(changeAdminTemplateCoverStatus(10, 'PUBLISHED', 'token')).resolves.toMatchObject({ status: 'PUBLISHED' })
    await expect(deleteAdminTemplateCover(10, 'token')).resolves.toBeNull()
    await expect(bindAdminTemplateCover(1001, 10, 'token')).resolves.toEqual({ templateId: 1001, coverAssetId: 10 })
    expect(fetchMock).toHaveBeenNthCalledWith(6, '/api/v1/admin/templates/1001/cover', expect.objectContaining({ method: 'PATCH', body: JSON.stringify({ coverAssetId: 10 }) }))
  })
})

function jsonResponse(body: unknown) {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}
