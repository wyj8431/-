import { render, screen } from '@testing-library/vue'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import LoginDialog from '@/features/auth/LoginDialog.vue'

describe('LoginDialog', () => {
  it('emits a WeChat authorization request instead of showing a static QR code', async () => {
    const user = userEvent.setup()
    const { emitted } = render(LoginDialog, { props: { open: true } })

    expect(screen.getByRole('dialog', { name: '登录后继续' })).toBeVisible()
    expect(screen.getByRole('textbox', { name: '手机号' })).toBeVisible()

    await user.click(screen.getByRole('button', { name: '微信登录在这里' }))

    expect(emitted('wechat-login')).toEqual([[]])
    expect(screen.queryByLabelText('微信登录二维码')).toBeNull()
    expect(screen.getByRole('textbox', { name: '手机号' })).toBeVisible()
  })

  it('switches to account password login and registration', async () => {
    const user = userEvent.setup()
    render(LoginDialog, { props: { open: true } })

    await user.click(screen.getByRole('button', { name: '账号密码登录' }))

    expect(screen.getByRole('heading', { name: '账号密码登录' })).toBeVisible()

    await user.click(screen.getByRole('button', { name: '手机号验证码登录' }))
    await user.click(screen.getByRole('button', { name: '手机号码注册' }))

    expect(screen.getByRole('heading', { name: '注册账号' })).toBeVisible()
  })

  it('toggles password visibility in account login mode', async () => {
    const user = userEvent.setup()
    render(LoginDialog, { props: { open: true } })

    await user.click(screen.getByRole('button', { name: '账号密码登录' }))
    const password = screen.getByLabelText('密码')
    expect(password).toHaveAttribute('type', 'password')
    expect(screen.getByRole('button', { name: '显示密码' })).toBeVisible()

    await user.type(password, 'secret123')
    await user.click(screen.getByRole('button', { name: '显示密码' }))

    expect(password).toHaveAttribute('type', 'text')
    expect(password).toHaveValue('secret123')
    expect(screen.getByRole('button', { name: '隐藏密码' })).toBeVisible()
  })

  it('renders local provider icons in the reference order', () => {
    render(LoginDialog, { props: { open: true } })

    expect(screen.getAllByRole('img').map((image) => image.getAttribute('alt'))).toEqual([
      '手机号登录',
      'QQ登录',
      '微博登录',
      '微信登录',
      '钉钉登录',
      '百度登录',
    ])
  })

  it('uses the WeChat provider icon to request WeChat authorization', async () => {
    const user = userEvent.setup()
    const { emitted } = render(LoginDialog, { props: { open: true } })

    await user.click(screen.getByRole('button', { name: '微信登录' }))

    expect(emitted('wechat-login')).toEqual([[]])
  })

  it('emits the existing phone verification payload', async () => {
    const user = userEvent.setup()
    const { emitted } = render(LoginDialog, { props: { open: true } })

    await user.type(screen.getByRole('textbox', { name: '手机号' }), '13800000000')
    await user.type(screen.getByRole('textbox', { name: '验证码' }), '123456')
    await user.click(screen.getByRole('button', { name: '登录' }))

    expect(emitted('login')).toEqual([[{ phone: '13800000000', verificationCode: '123456' }]])
  })
})
