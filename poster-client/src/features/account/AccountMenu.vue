<script setup lang="ts">
import { computed } from 'vue'
import { ChevronRight, Copy, Crown, KeyRound, LogOut, MessageCircle, ReceiptText, Settings2, Ticket, UsersRound, X } from 'lucide-vue-next'

const props = defineProps<{
  phone: string
  userId: number | null
  tenantRole: 'ADMIN' | 'USER' | 'OPERATOR' | null
}>()

const emit = defineEmits<{
  close: []
  account: []
  admin: []
  logout: []
  notice: [label: string]
}>()

const roleLabels = { ADMIN: '管理员', USER: '普通用户', OPERATOR: '运营' } as const
const canManage = computed(() => props.tenantRole === 'ADMIN' || props.tenantRole === 'OPERATOR')
</script>

<template>
  <section class="account-popover" role="dialog" aria-label="账户中心">
    <button class="account-popover-close" type="button" aria-label="关闭账户中心" @click="emit('close')"><X :size="17" /></button>
    <header class="account-profile">
      <div class="account-avatar"><span>all+</span></div>
      <div class="account-profile-copy"><strong>{{ phone }}</strong><small>{{ tenantRole ? roleLabels[tenantRole] : '个人版' }}</small></div>
      <button class="access-key" type="button" @click="emit('notice', 'Access Key')"><KeyRound :size="15" /> Access Key</button>
    </header>
    <p class="account-id">用户ID：{{ userId ?? '—' }} <button type="button" aria-label="复制用户ID" @click="emit('notice', '用户ID已复制')"><Copy :size="14" /></button></p>

    <section class="membership-panel">
      <div><strong><Crown :size="17" /> 免费版</strong><p>会员权益与高级工具将在后续阶段开放</p><small>模板发现 · 设计稿保存 · 团队空间</small></div>
      <button type="button" @click="emit('notice', '会员中心将在后续阶段开放')">了解会员 <ChevronRight :size="15" /></button>
    </section>

    <div class="account-metrics"><button type="button" @click="emit('notice', '空间详情将在后续阶段开放')"><span>存储空间</span><strong>—</strong><small>空间详情 <ChevronRight :size="13" /></small></button><button type="button" @click="emit('notice', 'AI 工具将在后续阶段开放')"><span>AI 积分</span><strong>—</strong><small>后续开放 <ChevronRight :size="13" /></small></button></div>

    <button class="account-team" type="button" @click="emit('notice', '团队空间将在后续阶段开放')"><span class="team-avatar">all+</span><span><strong>{{ phone }}</strong><small>个人版</small></span><span class="team-action"><UsersRound :size="17" /> 创建团队</span></button>
    <nav class="account-nav" aria-label="账户菜单">
      <button v-if="canManage" type="button" @click="emit('admin')"><UsersRound :size="20" />团队管理</button>
      <button type="button" @click="emit('notice', '订单发票将在后续阶段开放')"><ReceiptText :size="20" />订单/发票</button>
      <button type="button" @click="emit('notice', '授权记录将在后续阶段开放')"><Settings2 :size="20" />我的授权记录</button>
      <button type="button" @click="emit('notice', '消息中心将在后续阶段开放')"><MessageCircle :size="20" />消息中心</button>
      <button type="button" @click="emit('notice', '优惠券将在后续阶段开放')"><Ticket :size="20" />我的优惠券</button>
    </nav>
    <footer class="account-popover-footer"><button type="button" @click="emit('account')">进入个人中心 <ChevronRight :size="15" /></button><button class="logout-link" type="button" @click="emit('logout')"><LogOut :size="16" />退出登录</button></footer>
  </section>
</template>

<style scoped>
.account-popover { position: fixed; top: 67px; right: 27px; z-index: 50; width: min(456px, calc(100vw - 28px)); max-height: calc(100vh - 82px); overflow: auto; padding: 20px 18px 0; border: 1px solid #e7ebf3; border-radius: 14px; background: #fff; box-shadow: 0 18px 50px rgba(30, 47, 79, .18); color: #23304a; }
.account-popover-close { position: absolute; top: 10px; right: 10px; display: grid; width: 28px; height: 28px; place-items: center; border: 0; border-radius: 50%; background: transparent; color: #8994a8; }
.account-popover-close:hover { background: #f1f5fb; color: #287af4; }
.account-profile { display: flex; align-items: center; gap: 11px; padding-right: 39px; }
.account-avatar, .team-avatar { display: grid; flex: 0 0 auto; place-items: center; border-radius: 50%; background: #edf0f2; color: #b0b4b8; font-size: 11px; font-weight: 800; }
.account-avatar { width: 47px; height: 47px; }.team-avatar { width: 39px; height: 39px; }
.account-profile-copy { display: grid; flex: 1; gap: 4px; }.account-profile-copy strong { font-size: 17px; font-weight: 550; }.account-profile-copy small { color: #7e8ca4; font-size: 13px; }
.access-key { display: inline-flex; min-height: 31px; align-items: center; gap: 5px; padding: 0 9px; border: 0; border-radius: 7px; background: #edf0ff; color: #6364ee; font-size: 13px; }
.access-key:hover { background: #e2e4ff; }
.account-id { display: flex; align-items: center; gap: 4px; margin: 7px 0 16px 58px; color: #8797b4; font-size: 13px; }.account-id button { display: inline-grid; padding: 2px; border: 0; background: transparent; color: #8692a5; }
.membership-panel { display: flex; align-items: center; justify-content: space-between; gap: 16px; min-height: 114px; padding: 16px 18px; border-radius: 10px; background: #eaf1fc; }.membership-panel strong { display: flex; align-items: center; gap: 5px; font-size: 19px; }.membership-panel p { margin: 9px 0 4px; font-size: 13px; }.membership-panel small { color: #657791; font-size: 12px; }.membership-panel button { display: inline-flex; min-height: 39px; align-items: center; gap: 2px; flex: 0 0 auto; padding: 0 12px; border: 0; border-radius: 7px; background: #ffdec5; color: #915731; font-size: 13px; }
.account-metrics { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; margin-top: 16px; }.account-metrics button { display: grid; min-height: 92px; grid-template-columns: 1fr auto; gap: 6px 4px; padding: 14px; border: 1px solid #e1e7f1; border-radius: 10px; background: #fff; color: #2c3850; text-align: left; }.account-metrics button:hover { border-color: #9dc5ff; }.account-metrics span { font-size: 14px; }.account-metrics strong { grid-row: 2; font-size: 25px; }.account-metrics small { display: inline-flex; align-items: center; align-self: center; justify-self: end; color: #8390a5; font-size: 12px; }
.account-team { display: flex; width: 100%; align-items: center; gap: 10px; margin-top: 16px; padding: 11px 14px; border: 1px solid #e2e8f2; border-radius: 10px; background: #fff; color: #28364f; text-align: left; }.account-team:hover { border-color: #9fc7ff; background: #f8fbff; }.account-team > span:nth-child(2) { display: grid; flex: 1; gap: 3px; }.account-team small { color: #8794aa; font-size: 12px; }.team-action { display: inline-flex; align-items: center; gap: 4px; color: #5e6d86; font-size: 13px; }
.account-nav { display: grid; gap: 1px; margin: 15px 0 6px; }.account-nav button { display: flex; min-height: 36px; align-items: center; gap: 10px; padding: 0 12px; border: 0; border-radius: 7px; background: transparent; color: #2c3850; font-size: 14px; text-align: left; }.account-nav button:hover { background: #f3f7fd; color: #1e72ea; }.account-nav svg { width: 18px; height: 18px; color: #24344e; }
.account-popover-footer { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; margin: 11px -18px 0; padding: 12px 18px; border-top: 1px solid #ebeff5; }.account-popover-footer button { display: inline-flex; min-height: 31px; align-items: center; justify-content: center; gap: 4px; border: 0; background: transparent; color: #6d7b93; font-size: 13px; }.account-popover-footer button:hover { color: #1e72ea; }.account-popover-footer .logout-link { border-left: 1px solid #e7ecf4; }
@media (max-width: 600px) { .account-popover { top: 59px; right: 8px; bottom: 74px; width: calc(100vw - 16px); height: calc(100dvh - 133px); max-height: none; padding: 18px 15px 0; border-radius: 15px; }.account-profile { gap: 14px; padding-right: 45px; }.account-avatar { width: 55px; height: 55px; }.team-avatar { width: 45px; height: 45px; }.account-profile-copy { gap: 6px; }.account-profile-copy strong { font-size: 17px; }.access-key { display: none; }.account-id { margin-left: 69px; }.membership-panel { align-items: start; flex-direction: column; gap: 14px; padding: 17px; }.membership-panel button { min-height: 40px; }.account-metrics button { min-height: 100px; padding: 14px; }.account-popover-footer { margin: 12px -15px 0; padding: 13px 15px; } }
</style>
