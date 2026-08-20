<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ArrowLeft, CalendarRange, Pencil, Plus, RefreshCw, ServerCog, ShieldAlert, Trash2 } from 'lucide-vue-next'
import { ApiError } from '@/api/http'
import { changeAdminHomeTopicStatus, createAdminHomeTopic, deleteAdminHomeTopic, loadAdminHomeTopics, updateAdminHomeTopic } from '@/api/admin'
import type { AdminHomeTopic, CreateAdminHomeTopicInput, HomeTopicStatus, HomeTopicType } from '@/api/types'
import { useSessionStore } from '@/stores/session'

const session = useSessionStore()
const topics = ref<AdminHomeTopic[]>([])
const statusFilter = ref<'' | HomeTopicStatus>('')
const loading = ref(false)
const error = ref<Error | null>(null)
const mutationId = ref<number | null>(null)
const mutationError = ref<string | null>(null)
const formError = ref<string | null>(null)
const formSubmitting = ref(false)
const pendingDelete = ref<AdminHomeTopic | null>(null)
const deleteSubmitting = ref(false)
const deleteError = ref<string | null>(null)
const form = ref<(CreateAdminHomeTopicInput & { mode: 'create' | 'edit'; id: number | null; templateIdsText: string; startsAtText: string; endsAtText: string }) | null>(null)

const canAccess = computed(() => session.tenantRole === 'ADMIN' || session.tenantRole === 'OPERATOR')
const canEdit = computed(() => session.tenantRole === 'ADMIN')
const roleLabel = computed(() => session.tenantRole === 'ADMIN' ? '管理员' : session.tenantRole === 'OPERATOR' ? '运营' : '普通用户')
const statusLabels: Record<HomeTopicStatus, string> = { DRAFT: '草稿', PUBLISHED: '已发布', DISABLED: '已停用' }
const typeLabels: Record<HomeTopicType, string> = { HOTSPOT_CALENDAR: '热点日历', EDITORIAL_SCENE: '专题场景' }

async function load() {
  if (!canAccess.value) return
  loading.value = true
  error.value = null
  try { topics.value = await loadAdminHomeTopics(statusFilter.value || undefined, session.accessToken) }
  catch (cause) { error.value = toError(cause, '首页专题加载失败') }
  finally { loading.value = false }
}

function openCreate() {
  formError.value = null
  form.value = { mode: 'create', id: null, code: '', title: '', subtitle: null, type: 'EDITORIAL_SCENE', coverAssetId: null, startsAt: null, endsAt: null, sortOrder: 0, templateIds: [], templateIdsText: '', startsAtText: '', endsAtText: '' }
}

function openEdit(topic: AdminHomeTopic) {
  formError.value = null
  form.value = { mode: 'edit', id: topic.id, code: topic.code, title: topic.title, subtitle: topic.subtitle, type: topic.type, coverAssetId: topic.coverAssetId, startsAt: topic.startsAt, endsAt: topic.endsAt, sortOrder: topic.sortOrder, templateIds: [...topic.templateIds], templateIdsText: topic.templateIds.join(', '), startsAtText: toLocalDateTime(topic.startsAt), endsAtText: toLocalDateTime(topic.endsAt) }
}

async function submitForm() {
  if (!form.value || formSubmitting.value) return
  const current = form.value
  formSubmitting.value = true
  formError.value = null
  const input: CreateAdminHomeTopicInput = { code: current.code, title: current.title, subtitle: current.subtitle, type: current.type, coverAssetId: current.coverAssetId, startsAt: toInstant(current.startsAtText), endsAt: toInstant(current.endsAtText), sortOrder: Number(current.sortOrder), templateIds: current.templateIdsText.split(',').map((value) => Number(value.trim())).filter((value) => Number.isInteger(value) && value > 0) }
  try {
    const result = current.mode === 'create' ? await createAdminHomeTopic(input, session.accessToken) : await updateAdminHomeTopic(current.id!, input, session.accessToken)
    topics.value = current.mode === 'create' ? [...topics.value, result] : topics.value.map((item) => item.id === result.id ? result : item)
    form.value = null
  } catch (cause) { formError.value = toError(cause, '首页专题保存失败').message }
  finally { formSubmitting.value = false }
}

async function changeStatus(topic: AdminHomeTopic, event: Event) {
  if (!canEdit.value || mutationId.value !== null) return
  const status = (event.target as HTMLSelectElement).value as HomeTopicStatus
  if (!isStatus(status) || status === topic.status) return
  mutationId.value = topic.id; mutationError.value = null
  try { const result = await changeAdminHomeTopicStatus(topic.id, status, session.accessToken); topics.value = topics.value.map((item) => item.id === result.id ? result : item) }
  catch (cause) { mutationError.value = toError(cause, '首页专题状态更新失败').message }
  finally { mutationId.value = null }
}

function openDelete(topic: AdminHomeTopic) { pendingDelete.value = topic; deleteError.value = null }
async function confirmDelete() {
  if (!pendingDelete.value || deleteSubmitting.value) return
  const topic = pendingDelete.value; deleteSubmitting.value = true; deleteError.value = null
  try { await deleteAdminHomeTopic(topic.id, session.accessToken); topics.value = topics.value.filter((item) => item.id !== topic.id); pendingDelete.value = null }
  catch (cause) { deleteError.value = toError(cause, '首页专题删除失败').message }
  finally { deleteSubmitting.value = false }
}

function isForbidden(cause: Error | null) { return cause instanceof ApiError && cause.status === 403 }
function isStatus(value: string): value is HomeTopicStatus { return value === 'DRAFT' || value === 'PUBLISHED' || value === 'DISABLED' }
function toError(cause: unknown, fallback: string) { return cause instanceof Error ? cause : new Error(fallback) }
function toInstant(value: string) { return value ? new Date(value).toISOString() : null }
function toLocalDateTime(value: string | null) { return value ? value.slice(0, 16) : '' }
onMounted(() => { void load() })
</script>

<template>
  <div class="admin-page">
    <header class="admin-topbar"><a class="admin-brand" href="/"><span>all+</span><strong>poster</strong></a><div class="admin-topbar-title"><span>团队管理</span><i /></div><nav class="admin-nav" aria-label="管理导航"><a href="/admin">概览</a><a href="/admin/members">成员与角色</a><a href="/admin/templates">模板</a><a href="/admin/template-covers">模板封面</a><a class="active" href="/admin/home-topics">首页专题</a><a href="/admin/template-categories">模板分类</a><a href="/admin/template-tags">模板标签</a><a href="/admin#audit">审计记录</a></nav><div class="admin-user-context"><span>{{ session.phoneMasked ?? '当前账号' }}</span><b>{{ roleLabel }}</b></div></header>
    <main class="admin-main">
      <section class="admin-heading" aria-labelledby="topic-page-title"><div><p>内容运营</p><h1 id="topic-page-title">首页专题</h1></div><a class="admin-back-link" href="/"><ArrowLeft :size="17" />返回工作台</a></section>
      <section v-if="!canAccess || isForbidden(error)" class="admin-state admin-forbidden"><ShieldAlert :size="30" /><div><h2>无权访问首页专题</h2><p>当前账户没有团队管理权限。</p></div><a href="/">返回工作台</a></section>
      <section v-else class="admin-panel"><header class="admin-section-heading"><div><p>首页内容</p><h2>专题与热点日历</h2></div><div class="toolbar"><select v-model="statusFilter" aria-label="按状态筛选" @change="load"><option value="">全部状态</option><option value="DRAFT">草稿</option><option value="PUBLISHED">已发布</option><option value="DISABLED">已停用</option></select><button v-if="canEdit" class="admin-command" type="button" @click="openCreate"><Plus :size="16" />新增专题</button><button class="admin-reload" type="button" :disabled="loading" aria-label="刷新首页专题" @click="load"><RefreshCw :size="17" /><span>刷新</span></button></div></header>
        <p v-if="mutationError" class="admin-inline-error" role="alert">{{ mutationError }}</p><div v-if="loading" class="admin-loading" aria-label="正在加载首页专题"><div v-for="index in 4" :key="index" class="admin-skeleton" /></div><div v-else-if="error" class="admin-state admin-error"><ServerCog :size="26" /><div><h3>首页专题暂时无法加载</h3><p>{{ error.message }}</p></div><button type="button" @click="load"><RefreshCw :size="17" />重试</button></div><div v-else-if="topics.length === 0" class="admin-empty"><CalendarRange :size="25" /><strong>暂无首页专题</strong><p>当前筛选条件下没有专题。</p></div><div v-else class="table-wrap"><table class="topic-table"><thead><tr><th>专题</th><th>类型</th><th>模板</th><th>排序</th><th>状态</th><th v-if="canEdit">操作</th></tr></thead><tbody><tr v-for="topic in topics" :key="topic.id"><td><strong>{{ topic.title }}</strong><small>{{ topic.code }}</small></td><td>{{ typeLabels[topic.type] }}</td><td>{{ topic.templateIds.length }} 个</td><td>{{ topic.sortOrder }}</td><td><select :value="topic.status" :disabled="!canEdit || mutationId === topic.id" :aria-label="`修改 ${topic.title} 的状态`" @change="changeStatus(topic, $event)"><option v-for="(label, status) in statusLabels" :key="status" :value="status">{{ label }}</option></select></td><td v-if="canEdit" class="row-actions"><button class="icon-button" type="button" :aria-label="`编辑 ${topic.title}`" @click="openEdit(topic)"><Pencil :size="16" /></button><button class="icon-button danger" type="button" :aria-label="`删除 ${topic.title}`" @click="openDelete(topic)"><Trash2 :size="16" /></button></td></tr></tbody></table></div>
      </section>
    </main>
    <div v-if="form" class="dialog-backdrop"><form class="topic-dialog" role="dialog" aria-modal="true" @submit.prevent="submitForm"><h2>{{ form.mode === 'create' ? '新增首页专题' : '编辑首页专题' }}</h2><label>专题编码<input v-model="form.code" aria-label="专题编码" :readonly="form.mode === 'edit'" /></label><label>专题标题<input v-model="form.title" aria-label="专题标题" /></label><label>副标题<input v-model="form.subtitle" aria-label="专题副标题" /></label><label>专题类型<select v-model="form.type" aria-label="专题类型"><option value="EDITORIAL_SCENE">专题场景</option><option value="HOTSPOT_CALENDAR">热点日历</option></select></label><label>模板编号<input v-model="form.templateIdsText" aria-label="模板编号" placeholder="例如 1001, 1002" /></label><label>封面编号<input v-model.number="form.coverAssetId" type="number" min="1" aria-label="封面编号" /></label><div class="dialog-grid"><label>开始时间<input v-model="form.startsAtText" type="datetime-local" aria-label="开始时间" /></label><label>结束时间<input v-model="form.endsAtText" type="datetime-local" aria-label="结束时间" /></label></div><label>排序<input v-model.number="form.sortOrder" type="number" min="0" max="100000" aria-label="专题排序" /></label><p v-if="formError" class="dialog-error" role="alert">{{ formError }}</p><div class="dialog-actions"><button type="button" :disabled="formSubmitting" @click="form = null">取消</button><button type="submit" :disabled="formSubmitting">{{ formSubmitting ? '保存中' : '保存' }}</button></div></form></div>
    <div v-if="pendingDelete" class="dialog-backdrop"><div class="topic-dialog" role="alertdialog" aria-modal="true"><h2>确认删除专题</h2><p>确定删除“{{ pendingDelete.title }}”吗？</p><p v-if="deleteError" class="dialog-error" role="alert">{{ deleteError }}</p><div class="dialog-actions"><button type="button" @click="pendingDelete = null">取消</button><button type="button" :disabled="deleteSubmitting" @click="confirmDelete">{{ deleteSubmitting ? '删除中' : '确认删除' }}</button></div></div></div>
  </div>
</template>

<style scoped>
.admin-page { min-height: 100vh; background: #f4f7fb; color: #24324b; }.admin-topbar { display: flex; min-height: 66px; align-items: center; gap: 12px; padding: 0 clamp(16px, 4vw, 60px); border-bottom: 1px solid #dfe7f1; background: #fff; }.admin-brand { display: inline-flex; align-items: center; gap: 8px; color: #14233c; font-size: 22px; text-decoration: none; }.admin-brand span { display: inline-grid; width: 31px; height: 31px; place-items: center; border-radius: 8px; background: #0877ff; color: #fff; font-size: 12px; font-weight: 850; transform: rotate(-8deg); }.admin-brand strong { font-weight: 850; }.admin-topbar-title { color: #62728c; font-size: 14px; }.admin-nav { display: flex; gap: 2px; overflow-x: auto; }.admin-nav a { padding: 7px 6px; border-radius: 5px; color: #70819a; font-size: 11px; text-decoration: none; white-space: nowrap; }.admin-nav a:hover, .admin-nav a.active { background: #eaf4ff; color: #1673dc; }.admin-user-context { display: flex; align-items: center; gap: 8px; margin-left: auto; color: #5f708a; font-size: 12px; }.admin-user-context b { padding: 4px 8px; border-radius: 4px; background: #e7f2ff; color: #1673dc; }.admin-main { width: min(100% - 28px, 1180px); margin: 0 auto; padding: 34px 0 60px; }.admin-heading { display: flex; align-items: end; justify-content: space-between; gap: 18px; margin-bottom: 24px; }.admin-heading p { margin: 0 0 7px; color: #6882a3; font-size: 13px; }.admin-heading h1 { margin: 0; color: #1b2b45; font-size: 27px; }.admin-back-link, .admin-reload, .admin-command, .admin-state a, .admin-state button { display: inline-flex; min-height: 35px; align-items: center; justify-content: center; gap: 6px; padding: 0 11px; border: 1px solid #d8e2ef; border-radius: 5px; background: #fff; color: #4f698d; font-size: 13px; text-decoration: none; }.admin-panel { padding: 20px; border: 1px solid #dfe7f1; border-radius: 6px; background: #fff; }.admin-section-heading { display: flex; justify-content: space-between; gap: 14px; margin-bottom: 17px; }.admin-section-heading p { margin: 0 0 6px; color: #6882a3; font-size: 12px; }.admin-section-heading h2 { margin: 0; color: #263850; font-size: 18px; }.toolbar { display: flex; gap: 7px; }.toolbar select, .topic-table select { min-height: 34px; padding: 0 24px 0 8px; border: 1px solid #d7e1ed; border-radius: 4px; background: #fff; color: #536985; }.admin-inline-error, .dialog-error { color: #c55656; font-size: 13px; }.table-wrap { overflow-x: auto; }.topic-table { width: 100%; border-collapse: collapse; color: #50617a; font-size: 13px; }.topic-table th { padding: 0 11px 10px; border-bottom: 1px solid #e5ebf3; color: #8594a9; font-size: 12px; text-align: left; white-space: nowrap; }.topic-table td { padding: 12px 11px; border-bottom: 1px solid #edf1f6; white-space: nowrap; }.topic-table strong, .topic-table small { display: block; }.topic-table strong { color: #2d405c; }.topic-table small { margin-top: 4px; color: #8292a8; font-size: 11px; }.row-actions { display: flex; gap: 6px; }.icon-button { display: inline-flex; width: 32px; height: 32px; align-items: center; justify-content: center; border: 1px solid #d7e1ed; border-radius: 4px; background: #fff; color: #4f698d; }.icon-button.danger:hover { color: #c55656; border-color: #edb3b3; }.admin-loading { display: grid; gap: 8px; }.admin-skeleton { height: 52px; border-radius: 4px; background: #edf1f6; }.admin-state { display: flex; align-items: center; gap: 15px; min-height: 150px; padding: 24px; border: 1px solid #dfe7f1; border-radius: 6px; }.admin-state div { flex: 1; }.admin-empty { display: grid; justify-items: center; gap: 7px; min-height: 140px; align-content: center; color: #8a99ad; }.admin-empty strong { color: #52657f; }.dialog-backdrop { position: fixed; inset: 0; display: grid; place-items: center; padding: 18px; background: rgb(20 35 60 / 28%); z-index: 10; }.topic-dialog { width: min(100%, 480px); display: grid; gap: 13px; padding: 22px; border: 1px solid #dfe7f1; border-radius: 6px; background: #fff; }.topic-dialog h2 { margin: 0; color: #263850; font-size: 19px; }.topic-dialog label { display: grid; gap: 5px; color: #5f708a; font-size: 13px; }.topic-dialog input, .topic-dialog select { min-height: 35px; padding: 0 9px; border: 1px solid #d7e1ed; border-radius: 4px; color: #293b56; font: inherit; }.dialog-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }.dialog-actions { display: flex; justify-content: flex-end; gap: 8px; }.dialog-actions button { min-height: 35px; padding: 0 13px; border: 1px solid #d7e1ed; border-radius: 4px; background: #fff; color: #4f698d; }.dialog-actions button:last-child { border-color: #0877ff; background: #0877ff; color: #fff; }
</style>
