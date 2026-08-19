<script setup lang="ts">
import { Check, Copy, Link2, LogOut, Mail, Phone, ShieldCheck, UserRound, X } from 'lucide-vue-next'
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useSessionStore } from '@/stores/session'

const router = useRouter()
const session = useSessionStore()
const logoutOpen = ref(false)

const phone = computed(() => session.phoneMasked || '当前账号')
const roleLabels = { ADMIN: '管理员', USER: '普通用户', OPERATOR: '运营' } as const
const roleLabel = computed(() => session.tenantRole ? roleLabels[session.tenantRole] : '个人版')

async function confirmLogout() {
  try {
    await session.logout()
  } finally {
    logoutOpen.value = false
    void router.push('/')
  }
}
</script>

<template>
  <div class="account-page">
    <header class="account-topbar">
      <RouterLink class="account-brand" to="/"><span class="account-brand-mark">all+</span><strong>poster</strong></RouterLink>
      <span class="account-page-title">个人中心</span>
      <div class="account-topbar-actions"><RouterLink to="/">返回工作台</RouterLink><button type="button" @click="logoutOpen = true">退出登录</button></div>
    </header>
    <main class="account-content">
      <div class="account-content-meta"><span>账户设置</span><button type="button" @click="router.push('/')">返回首页 <span aria-hidden="true">›</span></button></div>

      <section class="account-section account-identity" aria-labelledby="identity-title">
        <h1 id="identity-title">个人信息</h1>
        <div class="identity-avatar"><UserRound :size="38" /></div>
        <button class="identity-upload" type="button" @click="router.push('/')">上传新头像</button>
        <div class="identity-row"><span>用户昵称</span><strong>{{ phone }}</strong><button type="button" @click="router.push('/')">编辑</button></div>
        <div class="identity-row"><span>用户ID</span><strong>{{ session.userId ?? '—' }}</strong><button class="copy-button" type="button" aria-label="复制用户ID"><Copy :size="17" /></button></div>
      </section>

      <section class="account-section" aria-labelledby="account-management-title">
        <h2 id="account-management-title">账号管理</h2>
        <div class="setting-row"><span><Phone :size="19" />手机号</span><strong>{{ phone }}</strong><button type="button" @click="router.push('/')">更换 <span aria-hidden="true">›</span></button></div>
        <div class="setting-row"><span><Mail :size="19" />邮箱</span><strong>未绑定</strong><button type="button" @click="router.push('/')">绑定 <span aria-hidden="true">›</span></button></div>
        <div class="setting-row"><span><ShieldCheck :size="19" />登录保护</span><strong>{{ roleLabel }}</strong><button type="button" @click="router.push('/')">查看 <span aria-hidden="true">›</span></button></div>
      </section>

      <section class="account-section" aria-labelledby="third-party-title">
        <h2 id="third-party-title">第三方账号管理</h2>
        <div class="setting-row"><span class="third-party"><span class="wechat-mark">微</span>微信账号</span><strong>未绑定</strong><button type="button" @click="router.push('/')">绑定 <span aria-hidden="true">›</span></button></div>
        <div class="setting-row"><span class="third-party"><Link2 :size="19" />其他登录方式</span><strong>后续开放</strong><button type="button" @click="router.push('/')">查看 <span aria-hidden="true">›</span></button></div>
      </section>

      <section class="account-section preference-row" aria-labelledby="preference-title">
        <div><h2 id="preference-title">个性化展示</h2><p>关闭后，我们将不会继续使用内容偏好进行推荐。</p></div><button class="toggle-on" type="button" aria-label="个性化展示已开启"><span /></button>
      </section>
      <button class="delete-account" type="button" @click="router.push('/')">删除账号</button>
    </main>

    <div v-if="logoutOpen" class="confirm-backdrop" role="presentation" @click.self="logoutOpen = false">
      <section class="confirm-dialog" role="dialog" aria-modal="true" aria-labelledby="logout-title"><button class="confirm-close" type="button" aria-label="关闭确认框" @click="logoutOpen = false"><X :size="20" /></button><h2 id="logout-title">确定要退出登录吗？</h2><div><button class="confirm-secondary" type="button" @click="logoutOpen = false">取消</button><button class="confirm-primary" type="button" @click="confirmLogout"><LogOut :size="16" />确定</button></div></section>
    </div>
  </div>
</template>

<style scoped>
.account-page { min-height: 100vh; background: #f6f7fb; color: #24324b; }
.account-topbar { display: flex; min-height: 72px; align-items: center; gap: 35px; padding: 0 48px; border-top: 2px solid #da9b9b; background: #fff; }
.account-brand { display: inline-flex; align-items: center; gap: 9px; color: #101521; font-size: 27px; font-weight: 850; }.account-brand-mark { display: inline-grid; width: 34px; height: 34px; place-items: center; border-radius: 12px 12px 12px 5px; background: #0f59e8; color: #fff; font-size: 14px; transform: rotate(-10deg); }.account-page-title { color: #3e5578; font-size: 20px; }.account-topbar-actions { display: flex; align-items: center; gap: 22px; margin-left: auto; color: #8a9bb5; font-size: 14px; }.account-topbar-actions a, .account-topbar-actions button { border: 0; background: transparent; color: inherit; }.account-topbar-actions a:hover, .account-topbar-actions button:hover { color: #116ff2; }
.account-content { width: min(100% - 44px, 1260px); margin: 0 auto; padding: 30px 0 80px; }.account-content-meta { display: flex; justify-content: space-between; margin-bottom: 21px; color: #91a0b7; font-size: 14px; }.account-content-meta button { display: inline-flex; align-items: center; gap: 5px; border: 0; background: transparent; color: #8da0bb; }.account-content-meta button:hover { color: #116ff2; }
.account-section { margin-bottom: 26px; padding: 42px 49px; border-radius: 10px; background: #fff; }.account-section h1, .account-section h2 { margin: 0 0 29px; color: #152843; font-size: 22px; }.account-section h2 { font-size: 20px; }.account-identity { position: relative; }.identity-avatar { display: grid; width: 96px; height: 96px; margin: 0 auto 20px; place-items: center; border-radius: 50%; background: #f0f1f2; color: #b3b6b9; }.identity-upload { display: block; margin: 0 auto 28px; border: 0; background: transparent; color: #0877ff; font-size: 15px; }.identity-upload:hover { text-decoration: underline; }
.identity-row, .setting-row { display: grid; min-height: 73px; grid-template-columns: 130px minmax(0, 1fr) auto; align-items: center; gap: 22px; border-top: 1px solid #edf0f5; color: #8296b2; }.identity-row strong, .setting-row strong { color: #17243a; font-size: 16px; font-weight: 500; }.identity-row button, .setting-row button { border: 0; background: transparent; color: #0877ff; font-size: 15px; }.identity-row button:hover, .setting-row button:hover { text-decoration: underline; }.copy-button { display: inline-grid; place-items: center; color: #6d7f99 !important; }.setting-row > span { display: inline-flex; align-items: center; gap: 8px; color: #7e94b1; font-size: 16px; }.third-party { color: #2f405f !important; }.wechat-mark { display: inline-grid; width: 25px; height: 25px; place-items: center; border-radius: 6px; background: #e8f7e8; color: #2abf43; font-size: 12px; font-weight: 800; }.preference-row { display: flex; align-items: center; justify-content: space-between; gap: 24px; }.preference-row h2 { margin-bottom: 12px; }.preference-row p { margin: 0; color: #7387a5; font-size: 14px; }.toggle-on { width: 46px; height: 26px; padding: 3px; border: 0; border-radius: 99px; background: #0c7bff; }.toggle-on span { display: block; width: 20px; height: 20px; margin-left: auto; border-radius: 50%; background: #fff; }.delete-account { display: block; margin: 0 0 0 auto; border: 0; background: transparent; color: #8a97aa; font-size: 14px; }.delete-account:hover { color: #db5566; }
.confirm-backdrop { position: fixed; inset: 0; z-index: 80; display: grid; place-items: center; background: rgba(28, 35, 50, .54); }.confirm-dialog { position: relative; width: min(100% - 34px, 430px); padding: 39px 44px 35px; border-radius: 9px; background: #fff; box-shadow: 0 24px 62px rgba(24, 36, 59, .25); }.confirm-dialog h2 { margin: 10px 0 37px; color: #28374f; font-size: 18px; font-weight: 500; }.confirm-close { position: absolute; top: 14px; right: 14px; display: grid; width: 34px; height: 34px; place-items: center; border: 0; border-radius: 50%; background: transparent; color: #6f7d92; }.confirm-close:hover { background: #f2f5fa; }.confirm-dialog > div { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; }.confirm-secondary, .confirm-primary { min-height: 46px; border-radius: 6px; font-size: 16px; }.confirm-secondary { border: 1px solid #dbe3ee; background: #fff; color: #24364f; }.confirm-primary { display: inline-flex; align-items: center; justify-content: center; gap: 6px; border: 0; background: #0877ff; color: #fff; }.confirm-secondary:hover { background: #f6f8fb; }.confirm-primary:hover { background: #0069ed; }
@media (max-width: 700px) { .account-topbar { min-height: 61px; gap: 15px; padding: 0 16px; }.account-brand { font-size: 21px; }.account-brand-mark { width: 29px; height: 29px; font-size: 12px; }.account-page-title { font-size: 16px; }.account-topbar-actions { gap: 11px; font-size: 12px; }.account-content { width: min(100% - 24px, 1260px); padding-top: 17px; }.account-section { margin-bottom: 14px; padding: 25px 18px; border-radius: 8px; }.account-section h1, .account-section h2 { margin-bottom: 23px; font-size: 18px; }.identity-row, .setting-row { grid-template-columns: 92px minmax(0, 1fr) auto; gap: 10px; min-height: 63px; }.identity-row span, .setting-row > span, .identity-row strong, .setting-row strong { font-size: 13px; }.identity-row button, .setting-row button { font-size: 12px; }.setting-row > span svg { width: 16px; }.preference-row { align-items: start; }.preference-row p { max-width: 225px; font-size: 12px; line-height: 1.55; }.account-content-meta { font-size: 12px; } }
</style>
