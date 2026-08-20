<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ArrowLeft, ImagePlus, RefreshCw, ShieldAlert, Trash2 } from 'lucide-vue-next'
import { changeAdminTemplateCoverStatus, completeAdminTemplateCover, deleteAdminTemplateCover, loadAdminTemplateCoverAssets, presignAdminTemplateCover } from '@/api/admin'
import { ApiError } from '@/api/http'
import type { AdminTemplateCoverAsset, TemplateCoverAssetStatus } from '@/api/types'
import { useSessionStore } from '@/stores/session'

const session = useSessionStore()
const covers = ref<AdminTemplateCoverAsset[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const mutationId = ref<number | null>(null)
const canAccess = computed(() => session.tenantRole === 'ADMIN' || session.tenantRole === 'OPERATOR')
const canEdit = computed(() => session.tenantRole === 'ADMIN')
const statuses: Record<TemplateCoverAssetStatus, string> = { DRAFT: '草稿', PUBLISHED: '已发布', DISABLED: '已停用' }

async function load() {
  if (!canAccess.value) return
  loading.value = true; error.value = null
  try { covers.value = await loadAdminTemplateCoverAssets(undefined, session.accessToken) }
  catch (cause) { error.value = cause instanceof Error ? cause.message : '封面加载失败' }
  finally { loading.value = false }
}

async function changeStatus(cover: AdminTemplateCoverAsset, event: Event) {
  if (!canEdit.value) return
  const status = (event.target as HTMLSelectElement).value as TemplateCoverAssetStatus
  mutationId.value = cover.id
  try {
    const result = await changeAdminTemplateCoverStatus(cover.id, status, session.accessToken)
    covers.value = covers.value.map((item) => item.id === cover.id ? result : item)
  }
  catch (cause) { error.value = cause instanceof Error ? cause.message : '状态更新失败' }
  finally { mutationId.value = null }
}

async function remove(cover: AdminTemplateCoverAsset) {
  if (!canEdit.value || !window.confirm('确认删除这个封面素材？')) return
  mutationId.value = cover.id
  try { await deleteAdminTemplateCover(cover.id, session.accessToken); covers.value = covers.value.filter((item) => item.id !== cover.id) }
  catch (cause) { error.value = cause instanceof ApiError && cause.code === 'TEMPLATE_COVER_IN_USE' ? '封面仍被模板或首页专题引用' : (cause instanceof Error ? cause.message : '删除失败') }
  finally { mutationId.value = null }
}

async function upload(file: File | undefined) {
  if (!file || !canEdit.value) return
  if (file.type !== 'image/jpeg' && file.type !== 'image/png' && file.type !== 'image/webp') {
    error.value = '仅支持 JPEG、PNG 或 WebP 图片'
    return
  }
  try {
    const bytes = new Uint8Array(await file.arrayBuffer())
    const digest = await crypto.subtle.digest('SHA-256', bytes)
    const sha256 = [...new Uint8Array(digest)].map((value) => value.toString(16).padStart(2, '0')).join('')
    const presign = await presignAdminTemplateCover({ fileName: file.name, mimeType: file.type, fileSize: file.size, sha256 }, session.accessToken)
    const response = await fetch(presign.uploadUrl, { method: 'PUT', headers: { 'Content-Type': file.type }, body: bytes })
    if (!response.ok) throw new Error('封面上传失败')
    await completeAdminTemplateCover(presign.sessionId, session.accessToken)
    await load()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '封面上传失败'
  }
}

onMounted(() => { void load() })
</script>

<template>
  <div class="admin-page">
    <header class="admin-topbar"><a class="admin-brand" href="/"><span>all+</span><strong>poster</strong></a><div class="admin-topbar-title"><span>团队管理</span><i /></div><nav class="admin-nav" aria-label="管理导航"><a href="/admin">概览</a><a href="/admin/templates">模板</a><a class="active" href="/admin/template-covers">模板封面</a><a href="/admin/template-categories">模板分类</a><a href="/admin/template-tags">模板标签</a></nav><div class="admin-user-context"><b>{{ session.tenantRole === 'ADMIN' ? '管理员' : '运营' }}</b></div></header>
    <main class="admin-main"><section class="admin-heading"><div><p>内容运营</p><h1>模板封面</h1></div><a class="admin-back-link" href="/"><ArrowLeft :size="17" />返回工作台</a></section>
      <section v-if="!canAccess" class="admin-state admin-forbidden"><ShieldAlert :size="30" /><div><h2>无权访问封面管理</h2><p>当前账户没有团队管理权限。</p></div></section>
      <section v-else class="admin-panel"><header class="admin-section-heading"><div><p>平台素材</p><h2>封面素材生命周期</h2></div><div class="toolbar"><label v-if="canEdit" class="admin-command"><ImagePlus :size="16" />上传封面<input type="file" accept="image/jpeg,image/png,image/webp" hidden @change="upload(($event.target as HTMLInputElement).files?.[0])" /></label><button class="admin-reload" type="button" :disabled="loading" @click="load"><RefreshCw :size="17" :class="{ spinning: loading }" />刷新</button></div></header>
        <p v-if="error" class="admin-inline-error" role="alert">{{ error }}</p><div v-if="loading" class="admin-loading">正在加载...</div><div v-else-if="covers.length === 0" class="admin-empty">暂无封面素材</div><div v-else class="table-wrap"><table class="cover-table"><thead><tr><th>封面</th><th>尺寸</th><th>大小</th><th>状态</th><th v-if="canEdit">操作</th></tr></thead><tbody><tr v-for="cover in covers" :key="cover.id"><td><img v-if="cover.id" :src="`/api/v1/template-cover-assets/${cover.id}/content`" alt="模板封面" /><code>#{{ cover.id }}</code></td><td>{{ cover.width }} × {{ cover.height }}</td><td>{{ (cover.fileSize / 1024).toFixed(1) }} KB</td><td><select :value="cover.status ?? 'DRAFT'" :disabled="!canEdit || mutationId === cover.id" @change="changeStatus(cover, $event)"><option v-for="(label, status) in statuses" :key="status" :value="status">{{ label }}</option></select></td><td v-if="canEdit"><button class="icon-button danger" type="button" :disabled="mutationId === cover.id" aria-label="删除封面" @click="remove(cover)"><Trash2 :size="16" /></button></td></tr></tbody></table></div>
      </section>
    </main>
  </div>
</template>

<style scoped>
.admin-page{min-height:100vh;background:#f4f7fb;color:#24324b}.admin-topbar{display:flex;min-height:66px;align-items:center;gap:18px;padding:0 clamp(20px,4vw,60px);border-bottom:1px solid #dfe7f1;background:#fff}.admin-brand{display:inline-flex;align-items:center;gap:8px;color:#14233c;font-size:22px;text-decoration:none}.admin-brand span{display:grid;width:31px;height:31px;place-items:center;border-radius:8px;background:#0877ff;color:#fff;font-size:12px}.admin-brand strong{font-weight:850}.admin-topbar-title{color:#62728c;font-size:14px}.admin-nav{display:flex;gap:4px}.admin-nav a{padding:7px 9px;border-radius:5px;color:#70819a;font-size:12px;text-decoration:none}.admin-nav a.active,.admin-nav a:hover{background:#eaf4ff;color:#1673dc}.admin-user-context{margin-left:auto}.admin-user-context b{padding:4px 8px;border-radius:4px;background:#e7f2ff;color:#1673dc;font-size:12px}.admin-main{width:min(100% - 40px,1180px);margin:auto;padding:46px 0}.admin-heading{display:flex;justify-content:space-between;margin-bottom:28px}.admin-heading p{margin:0 0 8px;color:#6882a3;font-size:13px}.admin-heading h1{margin:0;font-size:28px}.admin-back-link,.admin-reload,.admin-command,.icon-button{display:inline-flex;align-items:center;gap:6px;min-height:36px;padding:0 12px;border:1px solid #d8e2ef;border-radius:5px;background:#fff;color:#4f698d;font-size:13px;text-decoration:none}.admin-panel{padding:22px;border:1px solid #dfe7f1;border-radius:6px;background:#fff}.admin-section-heading{display:flex;justify-content:space-between;align-items:center;margin-bottom:18px}.admin-section-heading p{margin:0 0 5px;color:#6882a3;font-size:12px}.admin-section-heading h2{margin:0;font-size:18px}.toolbar{display:flex;gap:8px}.admin-command{cursor:pointer}.admin-state{padding:28px;background:#fff}.admin-inline-error{color:#c55656}.admin-empty,.admin-loading{padding:50px;text-align:center;color:#8a99ad}.table-wrap{overflow:auto}.cover-table{width:100%;border-collapse:collapse;font-size:13px}.cover-table th,.cover-table td{padding:12px;border-bottom:1px solid #edf1f6;text-align:left}.cover-table img{width:64px;height:64px;object-fit:cover;border-radius:4px;vertical-align:middle;margin-right:10px}.cover-table code{color:#506d96}.cover-table select{min-height:32px;padding:0 8px;border:1px solid #d7e1ed;border-radius:4px;background:#fff}.icon-button{width:32px;padding:0;justify-content:center}.danger:hover{color:#c55656}.spinning{animation:spin .9s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}
</style>
