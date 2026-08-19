import { render, screen } from '@testing-library/vue'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import AccountMenu from '@/features/account/AccountMenu.vue'

describe('AccountMenu', () => {
  it.each(['ADMIN', 'OPERATOR'] as const)('allows %s to open team management', async (tenantRole) => {
    const user = userEvent.setup()
    const { emitted } = render(AccountMenu, {
      props: { phone: '138****0000', userId: 7, tenantRole },
    })

    await user.click(screen.getByRole('button', { name: '团队管理' }))

    expect(emitted('admin')).toHaveLength(1)
  })

  it('does not show team management to a normal user', () => {
    render(AccountMenu, {
      props: { phone: '138****0000', userId: 7, tenantRole: 'USER' },
    })

    expect(screen.queryByRole('button', { name: '团队管理' })).not.toBeInTheDocument()
  })
})
