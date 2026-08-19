<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ArrowLeft, Hash, RefreshCw, ServerCog, ShieldAlert } from 'lucide-vue-next'
import { changeAdminTemplateTagStatus, loadAdminTemplateTags } from '@/api/admin'
import { ApiError } from '@/api/http'
import type { AdminTemplateTag, TemplateTagStatus } from '@/api/types'
import { useSessionStore } from '@/stores/session'

const session = useSessionStore()
const tags = ref<AdminTemplateTag[]>([])
const statusFilter = ref<'' | TemplateTagStatus>('')
const loading = ref(false)
const error = ref<Error | null>(null)
const mutationCode = ref<string | null>(null)
const mutationError = ref<string | null>(null)
const canAccess = computed(() => session.tenantRole === 'ADMIN' || session.tenantRole === 'OPERATOR')
const canEdit = computed(() => session.tenantRole === 'ADMIN')
const roleLabel = computed(() => session.tenantRole === 'ADMIN' ? '管理员' : session.tenantRole === 'OPERATOR' ? '运营' : '普通用户')
const statusLabels: Record<TemplateTagStatus, string> = { DRAFT: '草稿', PUBLISHED: '已发布', DISABLED: '已停用' }

async function load() {
  if (!canAccess.value) return
  loading.value = true
  error.value = null
  try {
    tags.value = await loadAdminTemplateTags(statusFilter.value || undefined, session.accessToken)
  } catch (cause) {
    error.value = toError(cause, '模板标签加载失败')
  } finally {
    loading.value = false
  }
}

async function changeStatus(tag: AdminTemplateTag, event: Event) {
  if (!canEdit.value || mutationCode.value !== null) return
  const status = (event.target as HTMLSelectElement | null)?.value as TemplateTagStatus | undefined
  if (!status || status === tag.status || !isStatus(status)) return
  mutationCode.value = tag.code
  mutationError.value = null
  try {
    const result = await changeAdminTemplateTagStatus(tag.code, status, session.accessToken)
    tags.value = tags.value.map((item) => item.code === result.code ? result : item)
  } catch (cause) {
    mutationError.value = toError(cause, '模板标签状态更新失败').message
  } finally {
    mutationCode.value = null
  }
}

function isForbidden(cause: Error | null) {
  return cause instanceof ApiError && cause.status === 403
}

function isStatus(value: string): value is TemplateTagStatus {
  return value === 'DRAFT' || value === 'PUBLISHED' || value === 'DISABLED'
}

function toError(cause: unknown, fallback: string) {
  return cause instanceof Error ? cause : new Error(fallback)
}

onMounted(() => { void load() })
</script>

<template>
  <div class="admin-page">
    <header class="admin-topbar">
      <a class="admin-brand" href="/"><span>all+</span><strong>poster</strong></a>
      <div class="admin-topbar-title"><span>团队管理</span><i aria-hidden="true" /></div>
      <nav class="admin-nav" aria-label="管理导航">
        <a href="/admin">概览</a><a href="/admin/members">成员与角色</a><a href="/admin/templates">模板</a><a href="/admin/template-categories">模板分类</a><a class="active" href="/admin/template-tags">模板标签</a><a href="/admin#audit">审计记录</a>
      </nav>
      <div class="admin-user-context"><span>{{ session.phoneMasked ?? '当前账号' }}</span><b>{{ roleLabel }}</b></div>
    </header>

    <main class="admin-main">
      <section class="admin-heading" aria-labelledby="tag-page-title"><div><p>内容运营</p><h1 id="tag-page-title">模板标签</h1></div><a class="admin-back-link" href="/"><ArrowLeft :size="17" />返回工作台</a></section>
      <section v-if="!canAccess || isForbidden(error)" class="admin-state admin-forbidden" aria-live="polite"><ShieldAlert :size="30" /><div><h2>无权访问标签管理</h2><p>当前账户没有团队管理权限。</p></div><a href="/">返回工作台</a></section>
      <section v-else class="admin-panel" aria-labelledby="tag-table-title">
        <header class="admin-section-heading"><div><p>模板运营</p><h2 id="tag-table-title">标签状态</h2></div><div class="toolbar"><select v-model="statusFilter" aria-label="按状态筛选" @change="load"><option value="">全部状态</option><option value="DRAFT">草稿</option><option value="PUBLISHED">已发布</option><option value="DISABLED">已停用</option></select><button class="admin-reload" type="button" :disabled="loading" aria-label="刷新模板标签" @click="load"><RefreshCw :size="17" :class="{ spinning: loading }" /><span>刷新</span></button></div></header>
        <p v-if="mutationError" class="admin-inline-error" role="alert">{{ mutationError }}</p>
        <div v-if="loading" class="admin-loading" aria-label="正在加载模板标签"><div v-for="index in 4" :key="index" class="admin-skeleton" /></div>
        <div v-else-if="error" class="admin-state admin-error" aria-live="polite"><ServerCog :size="26" /><div><h3>模板标签暂时无法加载</h3><p>{{ error.message }}</p></div><button type="button" @click="load"><RefreshCw :size="17" />重试</button></div>
        <div v-else-if="tags.length === 0" class="admin-empty" aria-live="polite"><Hash :size="25" /><strong>暂无模板标签</strong><p>当前筛选条件下没有标签。</p></div>
        <div v-else class="table-wrap"><table class="tag-table"><thead><tr><th scope="col">标签</th><th scope="col">编码</th><th scope="col">排序</th><th scope="col">状态</th></tr></thead><tbody><tr v-for="tag in tags" :key="tag.id"><td><strong>{{ tag.name }}</strong></td><td><code>{{ tag.code }}</code></td><td>{{ tag.sortOrder }}</td><td><select :value="tag.status" :disabled="!canEdit || mutationCode === tag.code" :aria-label="`修改 ${tag.name} 的状态`" @change="changeStatus(tag, $event)"><option v-for="(label, status) in statusLabels" :key="status" :value="status">{{ label }}</option></select></td></tr></tbody></table></div>
      </section>
    </main>
  </div>
</template>

<style scoped>
.admin-page { min-height: 100vh; background: #f4f7fb; color: #24324b; }.admin-topbar { display: flex; min-height: 66px; align-items: center; gap: 16px; padding: 0 clamp(20px, 4vw, 60px); border-bottom: 1px solid #dfe7f1; background: #fff; }.admin-brand { display: inline-flex; align-items: center; gap: 8px; color: #14233c; font-size: 22px; text-decoration: none; }.admin-brand span { display: inline-grid; width: 31px; height: 31px; place-items: center; border-radius: 8px; background: #0877ff; color: #fff; font-size: 12px; font-weight: 850; transform: rotate(-8deg); }.admin-brand strong { font-weight: 850; }.admin-topbar-title { display: inline-flex; align-items: center; gap: 13px; color: #62728c; font-size: 14px; }.admin-topbar-title i { width: 5px; height: 5px; border-radius: 50%; background: #b8c4d4; }.admin-nav { display: flex; align-items: center; gap: 3px; }.admin-nav a { padding: 7px 8px; border-radius: 5px; color: #70819a; font-size: 12px; text-decoration: none; }.admin-nav a:hover, .admin-nav a.active { background: #eaf4ff; color: #1673dc; }.admin-user-context { display: flex; align-items: center; gap: 10px; margin-left: auto; color: #5f708a; font-size: 13px; }.admin-user-context b { padding: 4px 8px; border-radius: 4px; background: #e7f2ff; color: #1673dc; font-size: 12px; font-weight: 650; }.admin-main { width: min(100% - 40px, 1180px); margin: 0 auto; padding: 46px 0 76px; }.admin-heading { display: flex; align-items: end; justify-content: space-between; gap: 24px; margin-bottom: 28px; }.admin-heading p, .admin-section-heading p { margin: 0 0 9px; color: #6882a3; font-size: 13px; font-weight: 650; }.admin-heading h1 { margin: 0; color: #1b2b45; font-size: 28px; font-weight: 760; }.admin-back-link, .admin-reload, .admin-state a, .admin-state button { display: inline-flex; min-height: 36px; align-items: center; justify-content: center; gap: 6px; padding: 0 12px; border: 1px solid #d8e2ef; border-radius: 5px; background: #fff; color: #4f698d; font-size: 13px; text-decoration: none; }.admin-back-link:hover, .admin-reload:hover:not(:disabled), .admin-state a:hover, .admin-state button:hover { border-color: #8ebcf5; color: #0877ff; }.admin-reload:disabled { cursor: wait; opacity: .7; }.admin-panel { padding: 22px; border: 1px solid #dfe7f1; border-radius: 6px; background: #fff; }.admin-section-heading { display: flex; align-items: center; justify-content: space-between; gap: 18px; margin-bottom: 18px; }.admin-section-heading h2 { margin: 0; color: #263850; font-size: 18px; }.toolbar { display: flex; gap: 8px; }.toolbar select, .tag-table select { min-height: 34px; padding: 0 27px 0 9px; border: 1px solid #d7e1ed; border-radius: 4px; background: #fff; color: #536985; font: inherit; }.admin-inline-error { margin: -4px 0 13px; color: #c55656; font-size: 13px; }.admin-loading { display: grid; gap: 8px; }.admin-skeleton { height: 51px; border-radius: 4px; background: linear-gradient(90deg, #edf1f6 25%, #f8fafc 38%, #edf1f6 55%); background-size: 400% 100%; animation: loading 1.35s ease infinite; }.admin-state { display: flex; align-items: center; gap: 16px; min-height: 160px; padding: 28px 30px; border: 1px solid #dfe7f1; border-radius: 6px; background: #fff; }.admin-state svg { flex: 0 0 auto; color: #6282aa; }.admin-state div { flex: 1; }.admin-state h2, .admin-state h3 { margin: 0 0 7px; color: #293b56; font-size: 18px; }.admin-state p { margin: 0; color: #7e90a8; font-size: 14px; }.admin-forbidden { border-color: #f0d9c7; background: #fffdfa; }.admin-forbidden svg { color: #c88340; }.admin-error { margin-top: 17px; }.admin-empty { display: grid; justify-items: center; gap: 8px; min-height: 142px; align-content: center; color: #8a99ad; text-align: center; }.admin-empty strong { color: #52657f; font-size: 15px; }.admin-empty p { margin: 0; font-size: 13px; }.table-wrap { overflow-x: auto; }.tag-table { width: 100%; border-collapse: collapse; color: #50617a; font-size: 13px; }.tag-table th { padding: 0 12px 11px; border-bottom: 1px solid #e5ebf3; color: #8594a9; font-size: 12px; font-weight: 650; text-align: left; white-space: nowrap; }.tag-table td { padding: 13px 12px; border-bottom: 1px solid #edf1f6; white-space: nowrap; }.tag-table tr:last-child td { border-bottom: 0; }.tag-table strong { color: #2d405c; font-weight: 650; }.tag-table code { color: #506d96; font: 12px ui-monospace, SFMono-Regular, Consolas, monospace; }.tag-table select:disabled { cursor: not-allowed; opacity: .72; }.spinning { animation: spin .9s linear infinite; } @keyframes spin { to { transform: rotate(360deg); } } @keyframes loading { to { background-position: -200% 0; } }
@media (max-width: 1060px) { .admin-topbar { gap: 7px; padding: 0 16px; }.admin-topbar-title { display: none; }.admin-nav { gap: 0; }.admin-nav a { padding: 6px 5px; font-size: 11px; }.admin-user-context span { display: none; }.admin-main { width: min(100% - 28px, 1180px); padding: 28px 0 50px; }.admin-heading h1 { font-size: 23px; }.admin-back-link span, .admin-reload span { display: none; }.admin-back-link, .admin-reload { width: 36px; min-height: 36px; padding: 0; }.admin-panel { padding: 17px 14px; }.admin-state { align-items: start; flex-wrap: wrap; padding: 22px; }.admin-state div { flex-basis: calc(100% - 46px); }.admin-state a, .admin-state button { margin-left: 46px; } }
</style>
