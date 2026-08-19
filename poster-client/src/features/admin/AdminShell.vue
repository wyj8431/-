<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Activity, ArrowLeft, ChevronLeft, ChevronRight, ClipboardList, Download, RefreshCw, ServerCog, ShieldAlert, UsersRound } from 'lucide-vue-next'
import { changeTenantRole, downloadAdminAuditLogs, loadAdminAuditLogs, loadAdminSummary, loadTenantMembers } from '@/api/admin'
import { ApiError } from '@/api/http'
import type { AdminAuditLog, AdminSummary, TenantMember, TenantRole } from '@/api/types'
import { useSessionStore } from '@/stores/session'

const session = useSessionStore()
const summary = ref<AdminSummary | null>(null)
const members = ref<TenantMember[]>([])
const loading = ref(false)
const error = ref<Error | null>(null)
const membersLoading = ref(false)
const membersError = ref<Error | null>(null)
const roleMutationUserId = ref<number | null>(null)
const roleMutationError = ref<string | null>(null)
const auditLogs = ref<AdminAuditLog[]>([])
const auditTotal = ref(0)
const auditPage = ref(1)
const auditLoading = ref(false)
const auditError = ref<Error | null>(null)
const auditExporting = ref(false)
const auditExportError = ref<string | null>(null)
const auditAction = ref('')
const auditOutcome = ref<'' | 'SUCCESS' | 'FAILURE'>('')
const auditFrom = ref('')
const auditTo = ref('')
const auditPageCount = computed(() => Math.max(1, Math.ceil(auditTotal.value / 20)))

const canAccess = computed(() => session.tenantRole === 'ADMIN' || session.tenantRole === 'OPERATOR')
const canEditRoles = computed(() => session.tenantRole === 'ADMIN')
const forbidden = computed(() => !canAccess.value || isForbidden(error.value) || isForbidden(membersError.value))
const roleLabel = computed(() => session.tenantRole === 'ADMIN' ? '管理员' : session.tenantRole === 'OPERATOR' ? '运营' : '普通用户')
const healthLabel = computed(() => summary.value?.health === 'UP' ? '服务正常' : '服务异常')
const memberRoleLabels: Record<TenantRole, string> = { ADMIN: '管理员', USER: '普通用户', OPERATOR: '运营' }
const memberStatusLabels = { ACTIVE: '正常', DISABLED: '已停用' } as const

async function load() {
  if (!canAccess.value) return
  loading.value = true
  membersLoading.value = true
  auditLoading.value = true
  error.value = null
  membersError.value = null
  auditError.value = null
  roleMutationError.value = null
  try {
    const [summaryResult, membersResult, auditResult] = await Promise.allSettled([
      loadAdminSummary(session.accessToken),
      loadTenantMembers({ page: 1, pageSize: 100 }, session.accessToken),
      loadAdminAuditLogs({
        page: auditPage.value,
        pageSize: 20,
        action: auditAction.value.trim() || undefined,
        outcome: auditOutcome.value || undefined,
        from: toIso(auditFrom.value),
        to: toIso(auditTo.value),
      }, session.accessToken),
    ])
    if (summaryResult.status === 'fulfilled') summary.value = summaryResult.value
    else error.value = toError(summaryResult.reason, '管理数据加载失败')
    if (membersResult.status === 'fulfilled') members.value = membersResult.value.items
    else membersError.value = toError(membersResult.reason, '成员列表加载失败')
    if (auditResult.status === 'fulfilled' && auditResult.value) {
      auditLogs.value = auditResult.value.items
      auditTotal.value = auditResult.value.total
      auditPage.value = auditResult.value.page
    } else if (auditResult.status === 'rejected') {
      auditError.value = toError(auditResult.reason, '审计记录加载失败')
    }
  } finally {
    loading.value = false
    membersLoading.value = false
    auditLoading.value = false
  }
}

async function loadAudits(resetPage = false) {
  if (!canAccess.value) return
  if (resetPage) auditPage.value = 1
  auditLoading.value = true
  auditError.value = null
  try {
    const result = await loadAdminAuditLogs({
      page: auditPage.value,
      pageSize: 20,
      action: auditAction.value.trim() || undefined,
      outcome: auditOutcome.value || undefined,
      from: toIso(auditFrom.value),
      to: toIso(auditTo.value),
    }, session.accessToken)
    auditLogs.value = result.items
    auditTotal.value = result.total
    auditPage.value = result.page
  } catch (cause) {
    auditError.value = toError(cause, '审计记录加载失败')
  } finally {
    auditLoading.value = false
  }
}

async function exportAudits() {
  if (!canAccess.value || auditExporting.value) return
  auditExporting.value = true
  auditExportError.value = null
  try {
    const blob = await downloadAdminAuditLogs(auditFilters(), session.accessToken)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = 'audit-logs.csv'
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
  } catch (cause) {
    auditExportError.value = toError(cause, '审计记录导出失败').message
  } finally {
    auditExporting.value = false
  }
}

function changeAuditPage(nextPage: number) {
  if (nextPage < 1 || nextPage > auditPageCount.value || nextPage === auditPage.value) return
  auditPage.value = nextPage
  void loadAudits()
}

async function loadMembers() {
  if (!canAccess.value) return
  membersLoading.value = true
  membersError.value = null
  try {
    members.value = (await loadTenantMembers({ page: 1, pageSize: 100 }, session.accessToken)).items
  } catch (cause) {
    membersError.value = toError(cause, '成员列表加载失败')
  } finally {
    membersLoading.value = false
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

function isForbidden(cause: Error | null) {
  return cause instanceof ApiError && cause.status === 403
}

function toError(cause: unknown, fallback: string) {
  return cause instanceof Error ? cause : new Error(fallback)
}

function isTenantRole(value: string): value is TenantRole {
  return value === 'ADMIN' || value === 'USER' || value === 'OPERATOR'
}

function toIso(value: string) {
  if (!value) return undefined
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? undefined : parsed.toISOString()
}

function auditFilters() {
  return {
    action: auditAction.value.trim() || undefined,
    outcome: auditOutcome.value || undefined,
    from: toIso(auditFrom.value),
    to: toIso(auditTo.value),
  }
}

onMounted(() => { void load() })
</script>

<template>
  <div class="admin-page">
    <header class="admin-topbar">
      <a class="admin-brand" href="/"><span>all+</span><strong>poster</strong></a>
      <div class="admin-topbar-title"><span>团队管理</span><i aria-hidden="true" /></div>
      <nav class="admin-nav" aria-label="管理导航">
        <a class="active" href="/admin">概览</a>
        <a href="/admin/members">成员与角色</a>
        <a href="/admin/templates">模板</a>
        <a href="/admin/template-categories">模板分类</a>
        <a href="/admin/template-tags">模板标签</a>
        <a href="#audit">审计记录</a>
      </nav>
      <div class="admin-user-context"><span>{{ session.phoneMasked ?? '当前账号' }}</span><b>{{ roleLabel }}</b></div>
    </header>

    <main class="admin-main">
      <section class="admin-heading" aria-labelledby="admin-title">
        <div>
          <p>管理工作台</p>
          <h1 id="admin-title">团队概览</h1>
        </div>
        <a class="admin-back-link" href="/"><ArrowLeft :size="17" />返回工作台</a>
      </section>

      <section v-if="forbidden" class="admin-state admin-forbidden" aria-live="polite">
        <ShieldAlert :size="30" />
        <div><h2>无权访问管理后台</h2><p>当前账户没有团队管理权限。</p></div>
        <a href="/">返回工作台</a>
      </section>

      <template v-else>
        <section class="admin-overview" aria-label="团队运行状态">
          <div class="admin-overview-copy"><span class="admin-status-dot" :class="{ alert: summary?.health !== 'UP' }" /><strong>{{ healthLabel }}</strong><small>团队 ID {{ session.tenantId ?? '—' }}</small></div>
          <button class="admin-reload" type="button" :disabled="loading" aria-label="刷新团队概览" @click="load"><RefreshCw :size="17" :class="{ spinning: loading }" /><span>刷新</span></button>
        </section>

        <section v-if="loading" class="admin-loading" aria-label="正在加载团队概览">
          <div v-for="index in 4" :key="index" class="admin-skeleton" />
        </section>

        <section v-else-if="error" class="admin-state admin-error" aria-live="polite">
          <ServerCog :size="30" />
          <div><h2>管理数据暂时无法加载</h2><p>{{ error.message }}</p></div>
          <button type="button" @click="load"><RefreshCw :size="17" />重试</button>
        </section>

        <template v-else-if="summary">
          <section class="admin-metrics" aria-label="团队汇总数据">
            <article class="admin-metric"><UsersRound :size="21" /><span>团队成员</span><strong>{{ summary.memberCount }}</strong><small>当前租户全部成员</small></article>
            <article class="admin-metric"><Activity :size="21" /><span>活跃用户</span><strong>{{ summary.activeUserCount }}</strong><small>状态为正常的成员</small></article>
            <article class="admin-metric"><ClipboardList :size="21" /><span>审计记录</span><strong>{{ summary.auditCount }}</strong><small>已记录的团队操作</small></article>
          </section>

          <section class="admin-activity" aria-labelledby="admin-activity-title">
            <div><span class="admin-activity-icon"><ServerCog :size="20" /></span><div><h2 id="admin-activity-title">会话与审计</h2><p>身份变更、登录与刷新操作均已纳入团队审计。</p></div></div>
            <span class="admin-health-chip" :class="{ alert: summary.health !== 'UP' }">{{ healthLabel }}</span>
          </section>
        </template>

        <section class="admin-members" aria-labelledby="admin-members-title">
          <header class="admin-section-heading">
            <div>
              <p>团队成员</p>
              <h2 id="admin-members-title">租户成员</h2>
            </div>
            <button class="admin-reload" type="button" :disabled="membersLoading" aria-label="刷新租户成员" @click="loadMembers">
              <RefreshCw :size="17" :class="{ spinning: membersLoading }" /><span>刷新</span>
            </button>
          </header>

          <p v-if="roleMutationError" class="admin-inline-error" role="alert">{{ roleMutationError }}</p>
          <div v-if="membersLoading" class="admin-members-loading" aria-label="正在加载租户成员">
            <div v-for="index in 3" :key="index" class="admin-member-skeleton" />
          </div>
          <div v-else-if="membersError" class="admin-state admin-error" aria-live="polite">
            <ServerCog :size="26" />
            <div><h3>成员列表暂时无法加载</h3><p>{{ membersError.message }}</p></div>
            <button type="button" @click="loadMembers"><RefreshCw :size="17" />重试</button>
          </div>
          <div v-else-if="members.length === 0" class="admin-empty" aria-live="polite">
            <UsersRound :size="25" /><strong>暂无租户成员</strong><p>当前筛选条件下没有成员记录。</p>
          </div>
          <div v-else class="admin-member-table-wrap">
            <table class="admin-member-table">
              <thead><tr><th scope="col">成员</th><th scope="col">角色</th><th scope="col">状态</th><th scope="col">加入时间</th></tr></thead>
              <tbody>
                <tr v-for="member in members" :key="member.userId">
                  <td><strong>{{ member.phoneMasked }}</strong><small>ID {{ member.userId }}</small></td>
                  <td>
                    <select
                      :value="member.tenantRole"
                      :disabled="!canEditRoles || roleMutationUserId === member.userId"
                      :aria-label="`修改 ${member.phoneMasked} 的角色`"
                      @change="updateRole(member, $event)"
                    >
                      <option v-for="(label, role) in memberRoleLabels" :key="role" :value="role">{{ label }}</option>
                    </select>
                  </td>
                  <td><span class="admin-member-status" :class="{ disabled: member.userStatus === 'DISABLED' }">{{ memberStatusLabels[member.userStatus] }}</span></td>
                  <td>{{ member.joinedAt ? new Date(member.joinedAt).toLocaleDateString('zh-CN') : '—' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <section id="audit" class="admin-audit" aria-labelledby="admin-audit-title">
          <header class="admin-section-heading admin-audit-heading">
            <div>
              <p>安全与追踪</p>
              <h2 id="admin-audit-title">审计记录</h2>
            </div>
            <div class="admin-audit-filters">
              <input v-model="auditAction" type="search" maxlength="64" placeholder="动作" aria-label="审计动作筛选" @keyup.enter="loadAudits(true)" />
              <select v-model="auditOutcome" aria-label="审计结果筛选" @change="loadAudits(true)">
                <option value="">全部结果</option>
                <option value="SUCCESS">成功</option>
                <option value="FAILURE">失败</option>
              </select>
              <input v-model="auditFrom" type="datetime-local" aria-label="审计开始时间" @change="loadAudits(true)" />
              <input v-model="auditTo" type="datetime-local" aria-label="审计结束时间" @change="loadAudits(true)" />
              <button class="admin-reload" type="button" :disabled="auditLoading" aria-label="应用审计筛选" @click="loadAudits(true)">
                <RefreshCw :size="16" :class="{ spinning: auditLoading }" /><span>筛选</span>
              </button>
              <button class="admin-reload" type="button" :disabled="auditLoading || auditExporting" aria-label="导出审计记录" @click="exportAudits">
                <Download :size="16" :class="{ spinning: auditExporting }" /><span>导出</span>
              </button>
            </div>
          </header>

          <p v-if="auditExportError" class="admin-inline-error" role="alert">{{ auditExportError }}</p>

          <div v-if="auditLoading" class="admin-members-loading" aria-label="正在加载审计记录">
            <div v-for="index in 3" :key="index" class="admin-member-skeleton" />
          </div>
          <div v-else-if="auditError" class="admin-state admin-error" aria-live="polite">
            <ServerCog :size="26" />
            <div><h3>审计记录暂时无法加载</h3><p>{{ auditError.message }}</p></div>
            <button type="button" @click="loadAudits()"><RefreshCw :size="17" />重试</button>
          </div>
          <div v-else-if="auditLogs.length === 0" class="admin-empty" aria-live="polite">
            <ClipboardList :size="25" /><strong>暂无审计记录</strong><p>当前筛选条件下没有事件。</p>
          </div>
          <template v-else>
            <div class="admin-member-table-wrap">
              <table class="admin-member-table admin-audit-table">
                <thead><tr><th scope="col">时间</th><th scope="col">操作者</th><th scope="col">动作</th><th scope="col">资源</th><th scope="col">结果</th><th scope="col">请求追踪号</th></tr></thead>
                <tbody>
                  <tr v-for="item in auditLogs" :key="item.id">
                    <td>{{ item.createdAt ? new Date(item.createdAt).toLocaleString('zh-CN') : '—' }}</td>
                    <td><strong>{{ item.actorPhoneMasked }}</strong><small v-if="item.actorUserId">ID {{ item.actorUserId }}</small></td>
                    <td><code>{{ item.action }}</code></td>
                    <td>{{ item.resourceType }}{{ item.resourceId ? ` #${item.resourceId}` : '' }}</td>
                    <td><span class="admin-audit-outcome" :class="{ failure: item.outcome === 'FAILURE' }">{{ item.outcome === 'SUCCESS' ? '成功' : '失败' }}</span></td>
                    <td><code>{{ item.requestId || '—' }}</code></td>
                  </tr>
                </tbody>
              </table>
            </div>
            <footer class="admin-audit-pagination" aria-label="审计记录分页">
              <span>第 {{ auditPage }} / {{ auditPageCount }} 页，共 {{ auditTotal }} 条</span>
              <div>
                <button type="button" :disabled="auditPage <= 1 || auditLoading" aria-label="上一页" @click="changeAuditPage(auditPage - 1)"><ChevronLeft :size="16" /></button>
                <button type="button" :disabled="auditPage >= auditPageCount || auditLoading" aria-label="下一页" @click="changeAuditPage(auditPage + 1)"><ChevronRight :size="16" /></button>
              </div>
            </footer>
          </template>
        </section>
      </template>
    </main>
  </div>
</template>

<style scoped>
.admin-page { min-height: 100vh; background: #f4f7fb; color: #24324b; }
.admin-topbar { display: flex; min-height: 66px; align-items: center; gap: 25px; padding: 0 clamp(20px, 4vw, 60px); border-bottom: 1px solid #dfe7f1; background: #fff; }
.admin-brand { display: inline-flex; align-items: center; gap: 8px; color: #14233c; font-size: 22px; text-decoration: none; }.admin-brand span { display: inline-grid; width: 31px; height: 31px; place-items: center; border-radius: 8px; background: #0877ff; color: #fff; font-size: 12px; font-weight: 850; transform: rotate(-8deg); }.admin-brand strong { font-weight: 850; }
.admin-topbar-title { display: inline-flex; align-items: center; gap: 13px; color: #62728c; font-size: 14px; }.admin-topbar-title i { width: 5px; height: 5px; border-radius: 50%; background: #b8c4d4; }.admin-nav { display: flex; align-items: center; gap: 4px; }.admin-nav a { padding: 7px 10px; border-radius: 5px; color: #70819a; font-size: 13px; text-decoration: none; }.admin-nav a:hover, .admin-nav a.active { background: #eaf4ff; color: #1673dc; }.admin-user-context { display: flex; align-items: center; gap: 10px; margin-left: auto; color: #5f708a; font-size: 13px; }.admin-user-context b { padding: 4px 8px; border-radius: 4px; background: #e7f2ff; color: #1673dc; font-size: 12px; font-weight: 650; }
.admin-main { width: min(100% - 40px, 1180px); margin: 0 auto; padding: 46px 0 76px; }.admin-heading { display: flex; align-items: end; justify-content: space-between; gap: 24px; margin-bottom: 28px; }.admin-heading p { margin: 0 0 9px; color: #6882a3; font-size: 13px; font-weight: 650; }.admin-heading h1 { margin: 0; color: #1b2b45; font-size: 28px; font-weight: 760; line-height: 1.2; }.admin-back-link, .admin-reload, .admin-state a, .admin-state button { display: inline-flex; min-height: 36px; align-items: center; justify-content: center; gap: 6px; padding: 0 12px; border: 1px solid #d8e2ef; border-radius: 5px; background: #fff; color: #4f698d; font-size: 13px; text-decoration: none; }.admin-back-link:hover, .admin-reload:hover:not(:disabled), .admin-state a:hover, .admin-state button:hover { border-color: #8ebcf5; color: #0877ff; }.admin-reload:disabled { cursor: wait; opacity: .7; }
.admin-overview { display: flex; min-height: 58px; align-items: center; justify-content: space-between; gap: 18px; padding: 0 18px; border: 1px solid #dfe8f3; background: #fff; }.admin-overview-copy { display: flex; align-items: center; gap: 9px; }.admin-overview-copy strong { color: #2c425e; font-size: 14px; }.admin-overview-copy small { margin-left: 5px; color: #8a9bb1; font-size: 12px; }.admin-status-dot { width: 8px; height: 8px; border-radius: 50%; background: #22b573; box-shadow: 0 0 0 4px #e0f8ec; }.admin-status-dot.alert { background: #d88039; box-shadow: 0 0 0 4px #fff0e2; }
.admin-metrics { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; margin-top: 17px; }.admin-metric { display: grid; min-height: 180px; align-content: start; grid-template-columns: 1fr auto; gap: 12px; padding: 22px; border: 1px solid #dfe7f1; border-radius: 6px; background: #fff; }.admin-metric svg { justify-self: end; color: #4b8bea; }.admin-metric span { color: #6d7e95; font-size: 14px; }.admin-metric strong { grid-column: 1 / -1; color: #1c2c45; font-size: 39px; font-weight: 720; line-height: 1; }.admin-metric small { grid-column: 1 / -1; color: #93a2b6; font-size: 12px; }
.admin-activity { display: flex; align-items: center; justify-content: space-between; gap: 20px; margin-top: 18px; padding: 20px 22px; border: 1px solid #dfe7f1; border-radius: 6px; background: #fff; }.admin-activity > div { display: flex; align-items: center; gap: 14px; }.admin-activity-icon { display: grid; width: 40px; height: 40px; place-items: center; border-radius: 5px; background: #eaf4ff; color: #1478e8; }.admin-activity h2 { margin: 0 0 5px; color: #263850; font-size: 15px; }.admin-activity p { margin: 0; color: #8090a5; font-size: 13px; }.admin-health-chip { padding: 5px 9px; border-radius: 4px; background: #e7f8ef; color: #209a65; font-size: 12px; font-weight: 650; }.admin-health-chip.alert { background: #fff0e2; color: #c36e29; }
.admin-loading { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; margin-top: 17px; }.admin-skeleton { height: 180px; border-radius: 6px; background: linear-gradient(90deg, #e7edf5 25%, #f4f7fb 38%, #e7edf5 55%); background-size: 400% 100%; animation: loading 1.35s ease infinite; }.admin-skeleton:last-child { display: none; }
.admin-state { display: flex; align-items: center; gap: 16px; min-height: 160px; padding: 28px 30px; border: 1px solid #dfe7f1; border-radius: 6px; background: #fff; }.admin-state svg { flex: 0 0 auto; color: #6282aa; }.admin-state div { flex: 1; }.admin-state h2 { margin: 0 0 7px; color: #293b56; font-size: 18px; }.admin-state p { margin: 0; color: #7e90a8; font-size: 14px; }.admin-forbidden { border-color: #f0d9c7; background: #fffdfa; }.admin-forbidden svg { color: #c88340; }.admin-error { margin-top: 17px; }
.admin-members { margin-top: 22px; padding: 22px; border: 1px solid #dfe7f1; border-radius: 6px; background: #fff; }.admin-section-heading { display: flex; align-items: center; justify-content: space-between; gap: 18px; margin-bottom: 18px; }.admin-section-heading p { margin: 0 0 5px; color: #6882a3; font-size: 12px; font-weight: 650; }.admin-section-heading h2 { margin: 0; color: #263850; font-size: 18px; }.admin-inline-error { margin: -4px 0 13px; color: #c55656; font-size: 13px; }.admin-members-loading { display: grid; gap: 8px; }.admin-member-skeleton { height: 51px; border-radius: 4px; background: linear-gradient(90deg, #edf1f6 25%, #f8fafc 38%, #edf1f6 55%); background-size: 400% 100%; animation: loading 1.35s ease infinite; }.admin-member-table-wrap { overflow-x: auto; }.admin-member-table { width: 100%; border-collapse: collapse; color: #50617a; font-size: 13px; }.admin-member-table th { padding: 0 12px 11px; border-bottom: 1px solid #e5ebf3; color: #8594a9; font-size: 12px; font-weight: 650; text-align: left; white-space: nowrap; }.admin-member-table td { padding: 13px 12px; border-bottom: 1px solid #edf1f6; vertical-align: middle; white-space: nowrap; }.admin-member-table tr:last-child td { border-bottom: 0; }.admin-member-table td:first-child { display: grid; gap: 4px; }.admin-member-table td:first-child strong { color: #2d405c; font-weight: 650; }.admin-member-table td:first-child small { color: #9aa7b8; font-size: 11px; }.admin-member-table select { min-height: 31px; padding: 0 27px 0 9px; border: 1px solid #d7e1ed; border-radius: 4px; background: #fff; color: #536985; font: inherit; }.admin-member-table select:disabled { cursor: not-allowed; opacity: .72; }.admin-member-status { display: inline-flex; padding: 4px 8px; border-radius: 4px; background: #e7f8ef; color: #209a65; font-size: 12px; }.admin-member-status.disabled { background: #f1f3f6; color: #8c98a8; }.admin-empty { display: grid; justify-items: center; gap: 8px; min-height: 142px; align-content: center; color: #8a99ad; text-align: center; }.admin-empty strong { color: #52657f; font-size: 15px; }.admin-empty p { margin: 0; font-size: 13px; }
.admin-audit { margin-top: 22px; padding: 22px; border: 1px solid #dfe7f1; border-radius: 6px; background: #fff; }.admin-audit-heading { align-items: end; }.admin-audit-filters { display: flex; align-items: center; gap: 8px; }.admin-audit-filters input, .admin-audit-filters select { min-height: 36px; padding: 0 10px; border: 1px solid #d7e1ed; border-radius: 4px; background: #fff; color: #536985; font: inherit; }.admin-audit-filters input { width: 130px; }.admin-audit-filters select { min-width: 106px; }.admin-audit-table code { color: #506d96; font: 12px ui-monospace, SFMono-Regular, Consolas, monospace; }.admin-audit-outcome { display: inline-flex; padding: 4px 8px; border-radius: 4px; background: #e7f8ef; color: #209a65; font-size: 12px; }.admin-audit-outcome.failure { background: #fff0e2; color: #c36e29; }.admin-audit-pagination { display: flex; align-items: center; justify-content: space-between; gap: 14px; padding-top: 15px; color: #8493a7; font-size: 12px; }.admin-audit-pagination > div { display: flex; gap: 6px; }.admin-audit-pagination button { display: grid; width: 32px; height: 32px; place-items: center; border: 1px solid #d8e2ef; border-radius: 4px; background: #fff; color: #557092; }.admin-audit-pagination button:hover:not(:disabled) { border-color: #8ebcf5; color: #0877ff; }.admin-audit-pagination button:disabled { cursor: not-allowed; opacity: .45; }
.spinning { animation: spin .9s linear infinite; } @keyframes spin { to { transform: rotate(360deg); } } @keyframes loading { to { background-position: -200% 0; } }
@media (max-width: 720px) { .admin-topbar { min-height: 58px; gap: 10px; padding: 0 16px; }.admin-brand { font-size: 19px; }.admin-brand span { width: 28px; height: 28px; }.admin-topbar-title { display: none; }.admin-nav { gap: 0; }.admin-nav a { padding: 6px 7px; font-size: 12px; }.admin-user-context span { display: none; }.admin-main { width: min(100% - 28px, 1180px); padding: 28px 0 50px; }.admin-heading { align-items: center; margin-bottom: 19px; }.admin-heading h1 { font-size: 23px; }.admin-back-link span, .admin-reload span { display: none; }.admin-back-link, .admin-reload { width: 36px; min-height: 36px; padding: 0; }.admin-overview { padding: 0 14px; }.admin-overview-copy small { display: none; }.admin-metrics, .admin-loading { grid-template-columns: 1fr; gap: 10px; }.admin-metric { min-height: 132px; padding: 17px; }.admin-metric strong { font-size: 31px; }.admin-activity { align-items: start; flex-direction: column; padding: 18px; }.admin-state { align-items: start; flex-wrap: wrap; padding: 22px; }.admin-state div { flex-basis: calc(100% - 46px); }.admin-state a, .admin-state button { margin-left: 46px; }.admin-members, .admin-audit { padding: 17px 14px; }.admin-member-table th, .admin-member-table td { padding-left: 8px; padding-right: 8px; }.admin-audit-heading { align-items: stretch; flex-direction: column; }.admin-audit-filters { flex-wrap: wrap; }.admin-audit-filters input { flex: 1; min-width: 110px; }.admin-audit-filters select { flex: 1; }.admin-audit-pagination { align-items: flex-start; flex-direction: column; } }
</style>
