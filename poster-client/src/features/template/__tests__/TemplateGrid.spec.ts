import { render, screen } from '@testing-library/vue'
import { describe, expect, it } from 'vitest'
import TemplateGrid from '@/features/template/TemplateGrid.vue'

describe('TemplateGrid', () => {
  it('uses a schema thumbnail without a cover URL', () => {
    render(TemplateGrid, {
      props: {
        templates: [{
          id: 1001,
          name: '朋友圈促销',
          width: 1080,
          height: 1440,
          coverAssetId: null,
          coverUrl: null,
          categoryCode: 'marketing',
          tagCodes: ['promotion'],
          publishedAt: null,
        }],
      },
    })

    expect(screen.getByLabelText('朋友圈促销模板预览')).toBeVisible()
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
  })
})
