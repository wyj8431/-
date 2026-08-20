<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ArrowLeft, ChevronLeft, ChevronRight, RefreshCw, ServerCog, ShieldAlert, UsersRound } from 'lucide-vue-next'
import { changeTenantRole, loadTenantMembers } from '@/api/admin'
import { ApiError } from '@/api/http'
import type { TenantMember, TenantRole } from '@/api/types'
import { useSessionStore } from '@/stores/session'

const session = useSessionStore()
const members = ref<TenantMember[]>([])
const page = ref(1)
const pageSize = 20
const total = ref(0)
const roleFilter = ref<'' | TenantRole>('')
const statusFilter = ref<'' | 'ACTIVE' | 'DISABLED'>('')
const loading = ref(false)
const error = ref<Error | null>(null)
const roleMutationUserId = ref<number | null>(null)
const roleMutationError = ref<string | null>(null)

const canAccess = computed(() => session.tenantRole === 'ADMIN' || session.tenantRole === 'OPERATOR')
const canEditRoles = computed(() => session.tenantRole === 'ADMIN')
const roleLabel = computed(() => session.tenantRole === 'ADMIN' ? '管理员' : session.tenantRole === 'OPERATOR' ? '运营' : '普通用户')
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))
const roleLabels: Record<TenantRole, string> = { ADMIN: '管理员', USER: '普通用户', OPERATOR: '运营' }
const statusLabels = { ACTIVE: '正常', DISABLED: '已停用' } as const

async function load(resetPage = false) {
  if (!canAccess.value) return
  if (resetPage) page.value = 1
  loading.value = true
  error.value = null
  try {
    const result = await loadTenantMembers({
      page: page.value,
      pageSize,
      role: roleFilter.value || undefined,
      status: statusFilter.value || undefined,
    }, session.accessToken)
    members.value = result.items
    total.value = result.total
    page.value = result.page
  } catch (cause) {
    error.value = toError(cause, '成员列表加载失败')
  } finally {
    loading.value = false
  }
}

async function updateRole(member: TenantMember, event: Event) {
  if (!canEditRoles.value || roleMutationUserId.value !== null) return
  const nextRole = (event.target as HTMLSelectElement | null)?.value as TenantRole | undefined
  if (!nextRole || nextRole === member.tenantRole || !isTenantRole(nextRole)) return
  roleMutationUserId.value = member.userId
  roleMutationError.value = null
  try {
    const result = await changeTenantRole(member.userId, nextRole, session.accessToken)
    members.value = members.value.map((item) => item.userId === result.userId
      ? { ...item, tenantRole: result.tenantRole }
      : item)
  } catch (cause) {
    roleMutationError.value = toError(cause, '成员角色更新失败').message
  } finally {
    roleMutationUserId.value = null
  }
}

function changePage(nextPage: number) {
  if (nextPage < 1 || nextPage > pageCount.value || nextPage === page.value) return
  page.value = nextPage
  void load()
}

function isForbidden(cause: Error | null) {
  return cause instanceof ApiError && cause.status === 403
}

function toError(cause: unknown, fallback: string) {
  return cause instanceof Error ? cause : new Error(fallback)
}

function isTenantRole(value: string): value is TenantRole {
  return value === 'ADMIN' || value === 'USER' || value === 'OPERATOR'
}

onMounted(() => { void load() })
</script>

<template>
  <div class="admin-page">
    <header class="admin-topbar">
      <a class="admin-brand" href="/"><span>all+</span><strong>poster</strong></a>
      <div class="admin-topbar-title"><span>团队管理</span><i aria-hidden="true" /></div>
      <nav class="admin-nav" aria-label="管理导航">
        <a href="/admin">概览</a>
        <a class="active" href="/admin/members">成员与角色</a>
        <a href="/admin/templates">模板</a>
        <a href="/admin/template-covers">模板封面</a>
        <a href="/admin/template-categories">模板分类</a>
        <a href="/admin/template-tags">模板标签</a>
        <a href="/admin#audit">审计记录</a>
      </nav>
      <div class="admin-user-context"><span>{{ session.phoneMasked ?? '当前账号' }}</span><b>{{ roleLabel }}</b></div>
    </header>

    <main class="admin-main">
      <section class="admin-heading" aria-labelledby="member-page-title">
        <div><p>权限管理</p><h1 id="member-page-title">成员与角色</h1></div>
        <a class="admin-back-link" href="/"><ArrowLeft :size="17" />返回工作台</a>
      </section>

      <section v-if="!canAccess || isForbidden(error)" class="admin-state admin-forbidden" aria-live="polite">
        <ShieldAlert :size="30" />
        <div><h2>无权访问成员管理</h2><p>当前账户没有团队管理权限。</p></div>
        <a href="/">返回工作台</a>
      </section>

      <section v-else class="admin-members-panel" aria-labelledby="member-table-title">
        <header class="admin-section-heading">
          <div><p>团队成员</p><h2 id="member-table-title">角色分配</h2></div>
          <button class="admin-reload" type="button" :disabled="loading" aria-label="刷新成员列表" @click="load()"><RefreshCw :size="17" :class="{ spinning: loading }" /><span>刷新</span></button>
        </header>

        <div class="admin-member-filters" aria-label="成员筛选">
          <select v-model="roleFilter" aria-label="按角色筛选">
            <option value="">全部角色</option>
            <option value="ADMIN">管理员</option>
            <option value="OPERATOR">运营</option>
            <option value="USER">普通用户</option>
          </select>
          <select v-model="statusFilter" aria-label="按状态筛选">
            <option value="">全部状态</option>
            <option value="ACTIVE">正常</option>
            <option value="DISABLED">已停用</option>
          </select>
          <button class="admin-filter-button" type="button" :disabled="loading" @click="load(true)"><RefreshCw :size="16" />应用筛选</button>
        </div>

        <p v-if="roleMutationError" class="admin-inline-error" role="alert">{{ roleMutationError }}</p>
        <div v-if="loading" class="admin-members-loading" aria-label="正在加载成员列表"><div v-for="index in 5" :key="index" class="admin-member-skeleton" /></div>
        <div v-else-if="error" class="admin-state admin-error" aria-live="polite">
          <ServerCog :size="26" /><div><h3>成员列表暂时无法加载</h3><p>{{ error.message }}</p></div><button type="button" @click="load()"><RefreshCw :size="17" />重试</button>
        </div>
        <div v-else-if="members.length === 0" class="admin-empty" aria-live="polite"><UsersRound :size="25" /><strong>暂无成员</strong><p>当前筛选条件下没有成员记录。</p></div>
        <template v-else>
          <div class="admin-member-table-wrap">
            <table class="admin-member-table">
              <thead><tr><th scope="col">成员</th><th scope="col">角色</th><th scope="col">状态</th><th scope="col">加入时间</th></tr></thead>
              <tbody>
                <tr v-for="member in members" :key="member.userId">
                  <td><strong>{{ member.phoneMasked }}</strong><small>ID {{ member.userId }}</small></td>
                  <td><select :value="member.tenantRole" :disabled="!canEditRoles || roleMutationUserId === member.userId" :aria-label="`修改 ${member.phoneMasked} 的角色`" @change="updateRole(member, $event)"><option v-for="(label, role) in roleLabels" :key="role" :value="role">{{ label }}</option></select></td>
                  <td><span class="admin-member-status" :class="{ disabled: member.userStatus === 'DISABLED' }">{{ statusLabels[member.userStatus] }}</span></td>
                  <td>{{ member.joinedAt ? new Date(member.joinedAt).toLocaleDateString('zh-CN') : '—' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <footer class="admin-pagination" aria-label="成员列表分页"><span>第 {{ page }} / {{ pageCount }} 页，共 {{ total }} 条</span><div><button type="button" :disabled="page <= 1 || loading" aria-label="上一页" @click="changePage(page - 1)"><ChevronLeft :size="16" /></button><button type="button" :disabled="page >= pageCount || loading" aria-label="下一页" @click="changePage(page + 1)"><ChevronRight :size="16" /></button></div></footer>
        </template>
      </section>
    </main>
  </div>
</template>

<style scoped>
.admin-page { min-height: 100vh; background: #f4f7fb; color: #24324b; }.admin-topbar { display: flex; min-height: 66px; align-items: center; gap: 22px; padding: 0 clamp(20px, 4vw, 60px); border-bottom: 1px solid #dfe7f1; background: #fff; }.admin-brand { display: inline-flex; align-items: center; gap: 8px; color: #14233c; font-size: 22px; text-decoration: none; }.admin-brand span { display: inline-grid; width: 31px; height: 31px; place-items: center; border-radius: 8px; background: #0877ff; color: #fff; font-size: 12px; font-weight: 850; transform: rotate(-8deg); }.admin-brand strong { font-weight: 850; }.admin-topbar-title { display: inline-flex; align-items: center; gap: 13px; color: #62728c; font-size: 14px; }.admin-topbar-title i { width: 5px; height: 5px; border-radius: 50%; background: #b8c4d4; }.admin-nav { display: flex; align-items: center; gap: 4px; }.admin-nav a { padding: 7px 10px; border-radius: 5px; color: #70819a; font-size: 13px; text-decoration: none; }.admin-nav a:hover, .admin-nav a.active { background: #eaf4ff; color: #1673dc; }.admin-user-context { display: flex; align-items: center; gap: 10px; margin-left: auto; color: #5f708a; font-size: 13px; }.admin-user-context b { padding: 4px 8px; border-radius: 4px; background: #e7f2ff; color: #1673dc; font-size: 12px; font-weight: 650; }.admin-main { width: min(100% - 40px, 1180px); margin: 0 auto; padding: 46px 0 76px; }.admin-heading { display: flex; align-items: end; justify-content: space-between; gap: 24px; margin-bottom: 28px; }.admin-heading p { margin: 0 0 9px; color: #6882a3; font-size: 13px; font-weight: 650; }.admin-heading h1 { margin: 0; color: #1b2b45; font-size: 28px; font-weight: 760; line-height: 1.2; }.admin-back-link, .admin-reload, .admin-filter-button, .admin-state a, .admin-state button { display: inline-flex; min-height: 36px; align-items: center; justify-content: center; gap: 6px; padding: 0 12px; border: 1px solid #d8e2ef; border-radius: 5px; background: #fff; color: #4f698d; font-size: 13px; text-decoration: none; }.admin-back-link:hover, .admin-reload:hover:not(:disabled), .admin-filter-button:hover:not(:disabled), .admin-state a:hover, .admin-state button:hover { border-color: #8ebcf5; color: #0877ff; }.admin-reload:disabled, .admin-filter-button:disabled { cursor: wait; opacity: .7; }.admin-members-panel { padding: 22px; border: 1px solid #dfe7f1; border-radius: 6px; background: #fff; }.admin-section-heading { display: flex; align-items: center; justify-content: space-between; gap: 18px; margin-bottom: 18px; }.admin-section-heading p { margin: 0 0 5px; color: #6882a3; font-size: 12px; font-weight: 650; }.admin-section-heading h2 { margin: 0; color: #263850; font-size: 18px; }.admin-member-filters { display: flex; gap: 8px; margin-bottom: 18px; }.admin-member-filters select { min-height: 36px; min-width: 130px; padding: 0 10px; border: 1px solid #d7e1ed; border-radius: 4px; background: #fff; color: #536985; font: inherit; }.admin-inline-error { margin: -4px 0 13px; color: #c55656; font-size: 13px; }.admin-members-loading { display: grid; gap: 8px; }.admin-member-skeleton { height: 51px; border-radius: 4px; background: linear-gradient(90deg, #edf1f6 25%, #f8fafc 38%, #edf1f6 55%); background-size: 400% 100%; animation: loading 1.35s ease infinite; }.admin-state { display: flex; align-items: center; gap: 16px; min-height: 160px; padding: 28px 30px; border: 1px solid #dfe7f1; border-radius: 6px; background: #fff; }.admin-state svg { flex: 0 0 auto; color: #6282aa; }.admin-state div { flex: 1; }.admin-state h2, .admin-state h3 { margin: 0 0 7px; color: #293b56; font-size: 18px; }.admin-state p { margin: 0; color: #7e90a8; font-size: 14px; }.admin-forbidden { border-color: #f0d9c7; background: #fffdfa; }.admin-forbidden svg { color: #c88340; }.admin-error { margin-top: 17px; }.admin-member-table-wrap { overflow-x: auto; }.admin-member-table { width: 100%; border-collapse: collapse; color: #50617a; font-size: 13px; }.admin-member-table th { padding: 0 12px 11px; border-bottom: 1px solid #e5ebf3; color: #8594a9; font-size: 12px; font-weight: 650; text-align: left; white-space: nowrap; }.admin-member-table td { padding: 13px 12px; border-bottom: 1px solid #edf1f6; vertical-align: middle; white-space: nowrap; }.admin-member-table tr:last-child td { border-bottom: 0; }.admin-member-table td:first-child { display: grid; gap: 4px; }.admin-member-table td:first-child strong { color: #2d405c; font-weight: 650; }.admin-member-table td:first-child small { color: #9aa7b8; font-size: 11px; }.admin-member-table select { min-height: 31px; padding: 0 27px 0 9px; border: 1px solid #d7e1ed; border-radius: 4px; background: #fff; color: #536985; font: inherit; }.admin-member-table select:disabled { cursor: not-allowed; opacity: .72; }.admin-member-status { display: inline-flex; padding: 4px 8px; border-radius: 4px; background: #e7f8ef; color: #209a65; font-size: 12px; }.admin-member-status.disabled { background: #f1f3f6; color: #8c98a8; }.admin-empty { display: grid; justify-items: center; gap: 8px; min-height: 142px; align-content: center; color: #8a99ad; text-align: center; }.admin-empty strong { color: #52657f; font-size: 15px; }.admin-empty p { margin: 0; font-size: 13px; }.admin-pagination { display: flex; align-items: center; justify-content: space-between; gap: 14px; padding-top: 15px; color: #8493a7; font-size: 12px; }.admin-pagination > div { display: flex; gap: 6px; }.admin-pagination button { display: grid; width: 32px; height: 32px; place-items: center; border: 1px solid #d8e2ef; border-radius: 4px; background: #fff; color: #557092; }.admin-pagination button:hover:not(:disabled) { border-color: #8ebcf5; color: #0877ff; }.admin-pagination button:disabled { cursor: not-allowed; opacity: .45; }.spinning { animation: spin .9s linear infinite; } @keyframes spin { to { transform: rotate(360deg); } } @keyframes loading { to { background-position: -200% 0; } }
@media (max-width: 800px) { .admin-topbar { gap: 12px; padding: 0 16px; }.admin-topbar-title { display: none; }.admin-nav { gap: 0; }.admin-nav a { padding: 6px 7px; font-size: 12px; }.admin-user-context span { display: none; }.admin-main { width: min(100% - 28px, 1180px); padding: 28px 0 50px; }.admin-heading h1 { font-size: 23px; }.admin-back-link span, .admin-reload span { display: none; }.admin-back-link, .admin-reload { width: 36px; min-height: 36px; padding: 0; }.admin-member-filters { flex-wrap: wrap; }.admin-member-filters select { flex: 1; min-width: 120px; }.admin-filter-button { flex: 1; }.admin-members-panel { padding: 17px 14px; }.admin-state { align-items: start; flex-wrap: wrap; padding: 22px; }.admin-state div { flex-basis: calc(100% - 46px); }.admin-state a, .admin-state button { margin-left: 46px; } }
</style>
