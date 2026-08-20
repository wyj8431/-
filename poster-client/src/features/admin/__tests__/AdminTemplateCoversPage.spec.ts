import { fireEvent, render, screen, waitFor } from '@testing-library/vue'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const adminApi = vi.hoisted(() => ({
  loadAdminTemplateCoverAssets: vi.fn(),
  changeAdminTemplateCoverStatus: vi.fn(),
  deleteAdminTemplateCover: vi.fn(),
  presignAdminTemplateCover: vi.fn(),
  completeAdminTemplateCover: vi.fn(),
}))
vi.mock('@/api/admin', () => adminApi)

import AdminTemplateCoversPage from '@/features/admin/AdminTemplateCoversPage.vue'
import { useSessionStore } from '@/stores/session'

const cover = {
  id: 50,
  objectKey: 'platform/template-cover/a.png',
  mimeType: 'image/png',
  fileSize: 4096,
  sha256: 'a'.repeat(64),
  width: 1080,
  height: 1440,
  status: 'DRAFT' as const,
  createdAt: null,
  updatedAt: null,
}

describe('AdminTemplateCoversPage', () => {
  let pinia: ReturnType<typeof createPinia>

  beforeEach(() => {
    pinia = createPinia()
    setActivePinia(pinia)
    Object.values(adminApi).forEach((mock) => mock.mockReset())
  })

  it('lets an administrator publish a platform cover', async () => {
    adminApi.loadAdminTemplateCoverAssets.mockResolvedValue([cover])
    adminApi.changeAdminTemplateCoverStatus.mockResolvedValue({ ...cover, status: 'PUBLISHED' })
    const session = useSessionStore()
    session.setSession({ accessToken: 'access-token', tokenType: 'Bearer', expiresIn: 900, userId: 7, tenantId: 11, tenantRole: 'ADMIN' }, '13800000000')

    render(AdminTemplateCoversPage, { global: { plugins: [pinia] } })
    const status = await screen.findByRole('combobox')
    await fireEvent.update(status, 'PUBLISHED')

    await waitFor(() => expect(adminApi.changeAdminTemplateCoverStatus).toHaveBeenCalledWith(50, 'PUBLISHED', 'access-token'))
    expect(status).toHaveValue('PUBLISHED')
  })

  it('keeps cover lifecycle controls read-only for an operator', async () => {
    adminApi.loadAdminTemplateCoverAssets.mockResolvedValue([cover])
    const session = useSessionStore()
    session.setSession({ accessToken: 'access-token', tokenType: 'Bearer', expiresIn: 900, userId: 7, tenantId: 11, tenantRole: 'OPERATOR' }, '13800000000')

    render(AdminTemplateCoversPage, { global: { plugins: [pinia] } })
    expect(await screen.findByRole('combobox')).toBeDisabled()
    expect(screen.queryByText('上传封面')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '删除封面' })).not.toBeInTheDocument()
  })
})
