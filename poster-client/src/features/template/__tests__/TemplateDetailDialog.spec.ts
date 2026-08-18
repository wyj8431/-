import { render, screen } from '@testing-library/vue'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import TemplateDetailDialog from '@/features/template/TemplateDetailDialog.vue'

describe('TemplateDetailDialog', () => {
  it('emits a use-template action from the detail view', async () => {
    const onUse = vi.fn()
    render(TemplateDetailDialog, {
      props: {
        open: true,
        template: {
          id: 1001,
          name: '朋友圈促销',
          width: 1080,
          height: 1440,
          coverAssetId: null,
          coverUrl: null,
          categoryCode: 'marketing',
          tagCodes: ['promotion'],
          publishedAt: null,
          schema: { schemaVersion: 1, canvas: { width: 1080, height: 1440 }, pages: [] },
          fields: [],
        },
        onUseTemplate: onUse,
      },
    })

    await userEvent.click(screen.getByRole('button', { name: '使用此模板' }))

    expect(onUse).toHaveBeenCalledWith(1001)
  })
})
