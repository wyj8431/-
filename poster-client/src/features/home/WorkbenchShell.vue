<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Bookmark,
  ChevronLeft,
  ChevronRight,
  CircleHelp,
  FilePlus2,
  LayoutDashboard,
  LogIn,
  Menu,
  Search,
  Settings2,
  Sparkles,
  UserRound,
} from 'lucide-vue-next'
import { loadTemplate, loadTemplateCategories } from '@/api/templates'
import { createDesign } from '@/api/designs'
import { login } from '@/api/auth'
import type { TemplateCategory, TemplateDetail, TemplateSummary } from '@/api/types'
import { useHomeStore } from '@/stores/home'
import { useNoticeStore } from '@/stores/notice'
import { useSessionStore } from '@/stores/session'
import { useTemplateQueryStore } from '@/stores/template-query'
import ModeTabs from './ModeTabs.vue'
import TemplateGrid from '@/features/template/TemplateGrid.vue'
import TemplateDetailDialog from '@/features/template/TemplateDetailDialog.vue'
import LoginDialog from '@/features/auth/LoginDialog.vue'
import CreateDesignDialog from '@/features/design/CreateDesignDialog.vue'
import DesignCreatedDialog from '@/features/design/DesignCreatedDialog.vue'

const router = useRouter()
const route = useRoute()
const home = useHomeStore()
const query = useTemplateQueryStore()
const session = useSessionStore()
const notice = useNoticeStore()

const categories = ref<TemplateCategory[]>([])
const activeMode = ref<'templates' | 'designs' | 'smart'>('templates')
const detailOpen = ref(false)
const detailLoading = ref(false)
const selectedSummary = ref<TemplateSummary | null>(null)
const selectedDetail = ref<TemplateDetail | null>(null)
const loginOpen = ref(false)
const loginLoading = ref(false)
const loginError = ref<string | null>(null)
const createOpen = ref(false)
const createLoading = ref(false)
const createdOpen = ref(false)
const createdDesign = ref<Awaited<ReturnType<typeof createDesign>> | null>(null)
const mobileMenuOpen = ref(false)
const phaseMessage = ref<string | null>(null)

const categoryName = computed(() => new Map(categories.value.map((category) => [category.code, category.name])))
const currentTotal = computed(() => query.data.total)
const pageLabel = computed(() => `${query.page} / ${query.totalPages}`)
const signedInLabel = computed(() => session.isAuthenticated ? '已登录' : '登录')

function showPhaseNotice(label: string) {
  phaseMessage.value = label
  notice.show(`${label}将在后续阶段开放`)
}

function selectMode(mode: 'templates' | 'designs' | 'smart') {
  activeMode.value = mode
  if (mode !== 'templates') showPhaseNotice(mode === 'smart' ? '智能创作' : '我的设计')
}

function selectTrend(tagCode: string) {
  void query.setTag(tagCode)
}

function selectTopic(code: string) {
  const tag = home.data?.trendingTags.find((item) => item.code === code)
  if (tag) selectTrend(tag.code)
  else notice.show('专题模板将在下一步接入')
}

async function openTemplate(summary: TemplateSummary) {
  selectedSummary.value = summary
  selectedDetail.value = null
  detailOpen.value = true
  detailLoading.value = true
  try {
    selectedDetail.value = await loadTemplate(summary.id)
  } catch (cause) {
    selectedDetail.value = {
      ...summary,
      schema: { schemaVersion: 1, canvas: { width: summary.width, height: summary.height }, pages: [] },
      fields: [],
    }
    notice.show(cause instanceof Error ? cause.message : '模板详情加载失败，已显示基础信息')
  } finally {
    detailLoading.value = false
  }
}

function startUseTemplate(templateId: number) {
  const summary = selectedSummary.value
  if (!summary || summary.id !== templateId) return
  detailOpen.value = false
  session.setPendingTemplateAction({ templateId: summary.id, name: summary.name })
  if (session.isAuthenticated) {
    session.pendingTemplateAction = { templateId: summary.id, name: summary.name }
    createOpen.value = true
    return
  }
  loginError.value = null
  loginOpen.value = true
}

async function submitLogin(payload: { phone: string; verificationCode: string }) {
  loginLoading.value = true
  loginError.value = null
  try {
    session.setSession(await login(payload.phone, payload.verificationCode))
    loginOpen.value = false
    await session.resumePendingAction(async (action) => {
      selectedSummary.value = query.data.items.find((item) => item.id === action.templateId) ?? {
        id: action.templateId,
        name: action.name,
        width: 1080,
        height: 1440,
        coverAssetId: null,
        coverUrl: null,
        categoryCode: null,
        tagCodes: [],
        publishedAt: null,
      }
      createOpen.value = true
    })
  } catch (cause) {
    loginError.value = cause instanceof Error ? cause.message : '登录失败，请重试'
  } finally {
    loginLoading.value = false
  }
}

function requestLogin() {
  loginError.value = null
  loginOpen.value = true
}

function confirmCreate(name: string) {
  const template = selectedSummary.value
  if (!template) return
  if (!session.isAuthenticated) {
    session.setPendingTemplateAction({ templateId: template.id, name: template.name })
    createOpen.value = false
    loginOpen.value = true
    return
  }
  void persistDesign(template.id, name)
}

async function persistDesign(templateId: number, name: string) {
  if (!session.accessToken) return
  createLoading.value = true
  try {
    createdDesign.value = await createDesign(templateId, name, session.accessToken)
    createOpen.value = false
    createdOpen.value = true
    session.pendingTemplateAction = null
  } catch (cause) {
    notice.show(cause instanceof Error ? cause.message : '设计稿创建失败，请重试')
  } finally {
    createLoading.value = false
  }
}

function newDesign() {
  if (query.data.items[0]) void openTemplate(query.data.items[0])
  else notice.show('请先选择一个模板')
}

function closeNotice() {
  phaseMessage.value = null
  notice.clear()
}

async function loadDiscovery() {
  await Promise.allSettled([home.load(), loadTemplateCategories().then((items) => { categories.value = items })])
  await query.runSearch()
}

onMounted(() => {
  query.bindRouter(router)
  void loadDiscovery()
})
</script>

<template>
  <div class="workbench-page">
    <aside class="rail" :class="{ 'rail-open': mobileMenuOpen }">
      <div class="brand"><span class="brand-mark">all+</span><span class="brand-meta">poster / 01</span></div>
      <nav class="rail-nav" aria-label="主导航">
        <button class="rail-link active" type="button"><LayoutDashboard :size="17" /> <span>工作台</span></button>
        <button class="rail-link" type="button" @click="selectMode('designs')"><Bookmark :size="17" /> <span>我的设计</span></button>
        <button class="rail-link" type="button" @click="selectMode('smart')"><Sparkles :size="17" /> <span>智能创作</span></button>
        <button class="rail-link disabled" type="button" @click="showPhaseNotice('团队空间')"><UserRound :size="17" /> <span>团队空间</span></button>
        <button class="rail-link disabled" type="button" @click="showPhaseNotice('设置')"><Settings2 :size="17" /> <span>设置</span></button>
      </nav>
      <div class="rail-footer">P0 · 工作台与模板发现<br />公开内容优先，创作链路逐步开放</div>
    </aside>

    <main class="main-shell">
      <header class="topbar">
        <div class="topbar-title">工作台 / 模板发现</div>
        <div class="topbar-actions">
          <button class="topbar-action" type="button" @click="showPhaseNotice('帮助中心')"><CircleHelp :size="16" /><span>帮助</span></button>
          <button v-if="!session.isAuthenticated" class="topbar-action primary" type="button" @click="requestLogin"><LogIn :size="16" /><span>登录</span></button>
          <button v-else class="topbar-action primary" type="button" @click="showPhaseNotice('账户中心')"><UserRound :size="16" /><span>{{ signedInLabel }}</span></button>
          <button class="icon-button mobile-menu-button" type="button" aria-label="打开导航" @click="mobileMenuOpen = !mobileMenuOpen"><Menu :size="18" /></button>
        </div>
      </header>

      <section class="workspace">
        <div class="hero-grid">
          <div>
            <p class="eyebrow">Workbench / template discovery</p>
            <h1 class="hero-title">把下一张海报，做得更快。</h1>
            <p class="hero-copy">从公开模板开始，先找到适合当下场景的版式，再用一份独立设计稿承接后续创作。</p>
          </div>
          <div class="hero-index"><span class="eyebrow">Current phase</span><strong>01</strong><span class="eyebrow">/ 06 · P0</span></div>
        </div>

        <ModeTabs :active="activeMode" @select="selectMode" />
        <p v-if="phaseMessage || notice.message" class="phase-note" role="status" @click="closeNotice"><strong>{{ phaseMessage ?? notice.message }}</strong><span>将在后续阶段开放</span></p>

        <div class="search-row">
          <label class="search-field">
            <Search :size="18" aria-hidden="true" />
            <span class="sr-only">搜索模板</span>
            <input :value="query.keyword" type="search" placeholder="搜索模板名称、用途或场景" @input="query.setKeyword(($event.target as HTMLInputElement).value)" />
          </label>
          <button class="new-design-button" type="button" @click="newDesign"><FilePlus2 :size="17" /> 新建设计</button>
        </div>
        <div v-if="home.data?.trendingTags.length" class="trend-row" aria-label="热门标签">
          <button v-for="tag in home.data.trendingTags" :key="tag.code" class="trend-chip" type="button" @click="selectTrend(tag.code)"># {{ tag.name }}</button>
        </div>

        <section v-if="home.data?.editorialScenes.length || home.data?.hotspotCalendar.length" aria-labelledby="topics-title">
          <div class="section-heading"><div><h2 id="topics-title">当下值得做</h2><p>编辑精选与热点日历</p></div><span class="section-count">{{ home.data?.editorialScenes.length ?? 0 }} 个专题</span></div>
          <div class="topic-strip">
            <button v-for="topic in home.data?.editorialScenes" :key="topic.code" class="topic-card" type="button" @click="selectTopic(topic.code)">
              <span class="eyebrow">Editorial / {{ topic.code }}</span><span class="topic-count">精选</span>
              <h3>{{ topic.title }}</h3><p>{{ topic.subtitle ?? '快速开始一张场景海报' }}</p>
            </button>
            <button v-for="topic in home.data?.hotspotCalendar" :key="topic.code" class="topic-card" type="button" @click="showPhaseNotice('热点日历')">
              <span class="eyebrow">Calendar / {{ topic.code }}</span><span class="topic-count">{{ topic.templateCount }} 款</span>
              <h3>{{ topic.title }}</h3><p>{{ topic.startsAt ? `开始于 ${topic.startsAt.slice(0, 10)}` : '近期场景' }}</p>
            </button>
          </div>
        </section>

        <div class="section-heading"><div><h2 id="templates-title">模板库</h2><p>公开发布 · 可直接预览</p></div><span class="section-count">{{ currentTotal }} 个结果</span></div>
        <div class="filter-bar" aria-label="模板筛选">
          <div class="filter-group">
            <select class="filter-select" :value="query.categoryCode" aria-label="按分类筛选" @change="query.setCategory(($event.target as HTMLSelectElement).value)">
              <option value="">全部分类</option>
              <option v-for="category in categories" :key="category.code" :value="category.code">{{ category.name }}</option>
            </select>
            <select class="filter-select" :value="query.tagCode" aria-label="按标签筛选" @change="query.setTag(($event.target as HTMLSelectElement).value)">
              <option value="">全部标签</option>
              <option v-for="tag in home.data?.trendingTags ?? []" :key="tag.code" :value="tag.code">{{ tag.name }}</option>
            </select>
          </div>
          <span v-if="query.categoryCode" class="section-count">{{ categoryName.get(query.categoryCode) ?? query.categoryCode }}</span>
        </div>

        <TemplateGrid :templates="query.data.items" :loading="query.loading" :error="query.error" @select="openTemplate" @retry="query.runSearch" />
        <nav v-if="query.data.total > 0" class="pager" aria-label="模板分页">
          <button type="button" aria-label="上一页" :disabled="query.page <= 1" @click="query.setPage(query.page - 1)"><ChevronLeft :size="17" /></button>
          <span>{{ pageLabel }}</span>
          <button type="button" aria-label="下一页" :disabled="query.page >= query.totalPages" @click="query.setPage(query.page + 1)"><ChevronRight :size="17" /></button>
        </nav>
      </section>
    </main>

    <nav class="bottom-nav" aria-label="移动端导航">
      <button class="active" type="button" @click="selectMode('templates')"><LayoutDashboard :size="17" />工作台</button>
      <button type="button" @click="selectMode('designs')"><Bookmark :size="17" />我的设计</button>
      <button type="button" aria-label="智能创作（后续阶段）" @click="selectMode('smart')"><Sparkles :size="17" />智能创作</button>
    </nav>

    <TemplateDetailDialog :open="detailOpen" :template="selectedDetail" :loading="detailLoading" @close="detailOpen = false" @use-template="startUseTemplate" />
    <LoginDialog :open="loginOpen" :loading="loginLoading" :error="loginError" @close="loginOpen = false" @login="submitLogin" />
    <CreateDesignDialog :open="createOpen" :template="selectedSummary" :loading="createLoading" @close="createOpen = false" @confirm="confirmCreate" />
    <DesignCreatedDialog :open="createdOpen" :design="createdDesign" @close="createdOpen = false" @continue="showPhaseNotice('编辑器')" />
  </div>
</template>

<style scoped>
.mobile-menu-button { display: none; }
.rail-link { width: 100%; border-top: 0; border-right: 0; border-bottom: 0; border-left: 2px solid transparent; text-align: left; }
.rail-link svg { flex: 0 0 auto; }
.rail-link.disabled { cursor: pointer; }
.topic-card { width: 100%; border-top: 0; border-right: 1px solid var(--line); border-bottom: 1px solid var(--line); border-left: 1px solid var(--line); text-align: left; }
.topic-card .eyebrow { display: inline-block; }
@media (max-width: 768px) {
  .mobile-menu-button { display: inline-grid; }
  .rail { transform: translateX(-100%); transition: transform .2s ease; }
  .rail.rail-open { transform: translateX(0); box-shadow: 10px 0 24px rgba(28,28,28,.12); }
}
</style>
