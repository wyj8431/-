<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  Bell,
  Bookmark,
  Boxes,
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  CircleHelp,
  CirclePlus,
  Grid2X2,
  ImagePlus,
  LayoutDashboard,
  Menu,
  PanelTop,
  Printer,
  Search,
  Sparkles,
  UserRound,
  Video,
  WandSparkles,
  X,
} from 'lucide-vue-next'
import { loadTemplate, loadTemplateCategories } from '@/api/templates'
import { createDesign } from '@/api/designs'
import { login } from '@/api/auth'
import type { TemplateCategory, TemplateDetail, TemplateSummary } from '@/api/types'
import { useHomeStore } from '@/stores/home'
import { useNoticeStore } from '@/stores/notice'
import { useSessionStore } from '@/stores/session'
import { useTemplateQueryStore } from '@/stores/template-query'
import TemplateGrid from '@/features/template/TemplateGrid.vue'
import TemplateDetailDialog from '@/features/template/TemplateDetailDialog.vue'
import LoginDialog from '@/features/auth/LoginDialog.vue'
import CreateDesignDialog from '@/features/design/CreateDesignDialog.vue'
import DesignCreatedDialog from '@/features/design/DesignCreatedDialog.vue'
import AccountMenu from '@/features/account/AccountMenu.vue'

const router = useRouter()
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
const accountOpen = ref(false)
const logoutOpen = ref(false)

const categoryName = computed(() => new Map(categories.value.map((category) => [category.code, category.name])))
const currentTotal = computed(() => query.data.total)
const pageLabel = computed(() => `${query.page} / ${query.totalPages}`)

type HomeMode = 'templates' | 'smart' | 'image' | 'video'

const homeModes: Array<{ mode: HomeMode; label: string; icon: typeof PanelTop }> = [
  { mode: 'templates', label: '设计模板', icon: PanelTop },
  { mode: 'smart', label: 'Agent 模式', icon: WandSparkles },
  { mode: 'image', label: '图片生成', icon: ImagePlus },
  { mode: 'video', label: '视频生成', icon: Video },
]

const creatorModules = [
  { title: '热点日历', subtitle: '一览全年热点', tone: 'calendar', icon: CalendarDays, action: 'calendar' },
  { title: 'AI 爆款视频', subtitle: '一键生成带货视频', tone: 'video', icon: Video, action: 'phase' },
  { title: '电商套图', subtitle: '一键生成爆款套图', tone: 'commerce', icon: Boxes, action: 'phase' },
  { title: '印刷定制', subtitle: '点击即可快速开始', tone: 'print', icon: Printer, action: 'phase' },
  { title: '智能抠图', subtitle: '一键抠图', tone: 'cutout', icon: WandSparkles, action: 'phase' },
  { title: 'AI 海报 / 封面', subtitle: '一键生成营销海报', tone: 'poster', icon: ImagePlus, action: 'phase' },
  { title: 'AI 去水印', subtitle: '无痕去水印', tone: 'watermark', icon: Sparkles, action: 'phase' },
  { title: '招商特辑', subtitle: '全场景模板', tone: 'recruit', icon: PanelTop, action: 'templates' },
]

const posterSamples = [
  { title: '情绪海报', caption: '内容表达', tone: 'emotion' },
  { title: '促销长图', caption: '营销推广', tone: 'promotion' },
  { title: '秋季养生', caption: '生活方式', tone: 'autumn' },
  { title: '慢生活', caption: '治愈系海报', tone: 'healing' },
  { title: '研学之旅', caption: '活动招募', tone: 'study' },
  { title: '节日物料', caption: '节日营销', tone: 'festival' },
  { title: '成人世界', caption: '观点表达', tone: 'adult' },
]

const calendarFallback = [
  { code: 'seasonal', title: '节日营销', startsAt: null, templateCount: 0 },
  { code: 'marketing', title: '营销推广', startsAt: null, templateCount: 0 },
  { code: 'social', title: '社交内容', startsAt: null, templateCount: 0 },
  { code: 'business', title: '商业宣传', startsAt: null, templateCount: 0 },
  { code: 'recruiting', title: '招聘宣传', startsAt: null, templateCount: 0 },
  { code: 'events', title: '活动预热', startsAt: null, templateCount: 0 },
]

const calendarItems = computed(() => home.data?.hotspotCalendar.length ? home.data.hotspotCalendar : calendarFallback)
const availableTemplates = computed(() => query.data.items.length ? query.data.items : (home.data?.featuredTemplates ?? []))

function showPhaseNotice(label: string) {
  phaseMessage.value = label
  notice.show(`${label}将在后续阶段开放`)
}

function selectMode(mode: 'templates' | 'designs' | 'smart') {
  activeMode.value = mode
  if (mode !== 'templates') showPhaseNotice(mode === 'smart' ? '智能创作' : '我的设计')
}

function selectHomeMode(mode: HomeMode) {
  if (mode === 'templates') {
    activeMode.value = 'templates'
    return
  }
  showPhaseNotice(mode === 'smart' ? 'Agent 模式' : mode === 'image' ? '图片生成' : '视频生成')
}

function selectTrend(tagCode: string) {
  void query.setTag(tagCode)
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
    session.setSession(await login(payload.phone, payload.verificationCode), payload.phone)
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

function requestLogout() {
  accountOpen.value = false
  logoutOpen.value = true
}

async function confirmLogout() {
  try {
    await session.logout()
  } finally {
    logoutOpen.value = false
    notice.show('已退出登录')
  }
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
  if (availableTemplates.value[0]) void openTemplate(availableTemplates.value[0])
  else notice.show('请先选择一个模板')
}

function openSample() {
  if (availableTemplates.value[0]) void openTemplate(availableTemplates.value[0])
  else notice.show('模板正在准备中')
}

function selectCreatorModule(action: string, title: string) {
  if (action === 'templates') {
    activeMode.value = 'templates'
    return
  }
  if (action === 'calendar') {
    document.getElementById('hotspot-calendar')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    return
  }
  showPhaseNotice(title)
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
  <div class="workbench-page creator-home">
    <aside class="rail" :class="{ 'rail-open': mobileMenuOpen }">
      <button class="rail-collapse" type="button" aria-label="折叠导航" @click="mobileMenuOpen = !mobileMenuOpen"><Menu :size="20" /></button>
      <nav class="rail-nav" aria-label="主导航">
        <button class="rail-link active" type="button"><LayoutDashboard :size="21" /><span>首页</span></button>
        <button class="rail-link" type="button" @click="selectMode('templates')"><PanelTop :size="21" /><span>模板</span></button>
        <button class="rail-link" type="button" aria-label="智能创作" @click="selectMode('smart')"><Sparkles :size="21" /><span>AI 工具</span></button>
        <button class="rail-link" type="button" @click="newDesign"><CirclePlus :size="21" /><span>创建</span></button>
        <span class="rail-divider" aria-hidden="true" />
        <button class="rail-link" type="button" @click="selectMode('designs')"><UserRound :size="21" /><span>我的</span></button>
        <button class="rail-link" type="button" @click="showPhaseNotice('团队空间')"><Boxes :size="21" /><span>团队</span></button>
      </nav>
      <div class="rail-bottom">
        <button class="rail-link" type="button" @click="showPhaseNotice('团队空间')"><Boxes :size="21" /><span>团队</span></button>
        <button class="rail-link" type="button" @click="showPhaseNotice('帮助中心')"><CircleHelp :size="21" /><span>帮助</span></button>
      </div>
    </aside>

    <main class="main-shell">
      <header class="topbar">
        <div class="brand brand-top"><span class="brand-mark">all+</span><span class="brand-word">poster</span></div>
        <div class="topbar-actions">
          <button class="topbar-action" type="button" @click="showPhaseNotice('AI 图表')">AI 图表</button>
          <button class="topbar-action print-link" type="button" @click="showPhaseNotice('印刷店')">印刷店</button>
          <button class="topbar-icon" type="button" aria-label="应用中心" @click="showPhaseNotice('应用中心')"><Grid2X2 :size="20" /></button>
          <button class="topbar-icon" type="button" aria-label="通知" @click="showPhaseNotice('通知')"><Bell :size="20" /></button>
          <button class="vip-button" type="button" @click="showPhaseNotice('会员中心')"><span>VIP</span> 开通会员</button>
          <button v-if="!session.isAuthenticated" class="login-register-button" type="button" @click="requestLogin">登录注册</button>
          <div v-else class="account-anchor">
            <button class="avatar-button signed" type="button" aria-label="账户中心" :aria-expanded="accountOpen" @click="accountOpen = !accountOpen"><UserRound :size="18" /></button>
            <AccountMenu v-if="accountOpen" :phone="session.phoneMasked ?? '当前账号'" :user-id="session.userId" :tenant-role="session.tenantRole" @close="accountOpen = false" @account="router.push('/account')" @admin="accountOpen = false; router.push('/admin')" @logout="requestLogout" @notice="(label) => showPhaseNotice(label)" />
          </div>
          <button class="icon-button mobile-menu-button" type="button" aria-label="打开导航" @click="mobileMenuOpen = !mobileMenuOpen"><Menu :size="20" /></button>
        </div>
      </header>

      <section class="workspace">
        <section class="creator-hero" aria-labelledby="creator-title">
          <h1 id="creator-title">今天你想做些什么？</h1>
          <div class="creation-console">
            <nav class="mode-tabs" aria-label="创作模式">
              <button v-for="mode in homeModes" :key="mode.mode" class="mode-tab" :class="{ active: activeMode === mode.mode }" type="button" :aria-label="mode.mode === 'smart' ? '智能创作（后续阶段）' : mode.label" @click="selectHomeMode(mode.mode)">
                <component :is="mode.icon" :size="18" aria-hidden="true" />
                {{ mode.label }}
              </button>
            </nav>
            <form class="search-field" @submit.prevent="query.runSearch">
              <Search :size="21" aria-hidden="true" />
              <label class="sr-only" for="home-template-search">搜索模板</label>
              <input id="home-template-search" :value="query.keyword" type="search" placeholder="输入关键词搜索你想要的模板或素材" @input="query.setKeyword(($event.target as HTMLInputElement).value)" />
              <button class="camera-button" type="button" aria-label="图片搜索" @click="showPhaseNotice('图片搜索')"><ImagePlus :size="21" /></button>
              <button class="search-submit" type="submit">搜索</button>
            </form>
          </div>
          <div v-if="home.data?.trendingTags.length" class="trend-row" aria-label="热门标签">
            <button v-for="tag in home.data.trendingTags" :key="tag.code" class="trend-chip" type="button" @click="selectTrend(tag.code)"># {{ tag.name }}</button>
            <button class="trend-chip" type="button" @click="showPhaseNotice('更多场景')">无限画布</button>
            <button class="trend-chip" type="button" @click="showPhaseNotice('AI 电商')">AI 电商</button>
            <button class="trend-chip" type="button" @click="showPhaseNotice('喜报')">喜报</button>
          </div>
        </section>

        <p v-if="phaseMessage || notice.message" class="phase-note" role="status" @click="closeNotice"><strong>{{ phaseMessage ?? notice.message }}</strong><span>将在后续阶段开放</span></p>

        <section class="creator-dashboard" aria-label="快速创作与热门推荐">
          <div class="quick-create-column">
            <button class="create-card" type="button" @click="newDesign">
              <span class="create-card-copy"><strong>创建设计</strong><small>高频创作场景 · 一键直达</small><span class="round-arrow"><ChevronRight :size="20" /></span></span>
              <span class="create-card-art"><CirclePlus :size="42" stroke-width="1.7" /></span>
            </button>
            <div class="quick-create-row">
              <button class="small-create-card" type="button" @click="showPhaseNotice('无限画布')"><span><strong>无限画布</strong><small>点击即可快速开始</small></span><Boxes :size="27" /></button>
              <button class="small-create-card" type="button" @click="showPhaseNotice('图片编辑')"><span><strong>图片编辑</strong><small>点击即可快速开始</small></span><ImagePlus :size="27" /></button>
            </div>
          </div>
          <section class="feature-deck">
            <div class="feature-deck-header">
              <div class="feature-tabs"><button class="feature-tab active" type="button">热门推荐</button><button class="feature-tab" type="button" @click="showPhaseNotice('AI 电商')">AI 电商</button><button class="feature-tab" type="button" @click="showPhaseNotice('模特穿戴')">模特穿戴</button><button class="feature-tab" type="button" @click="showPhaseNotice('视频创作')">视频创作</button><button class="feature-tab" type="button" @click="showPhaseNotice('POD 印花')">POD 印花</button></div>
              <button class="more-link" type="button" @click="showPhaseNotice('更多工具')">更多 <ChevronRight :size="15" /></button>
            </div>
            <div class="feature-grid">
              <button v-for="module in creatorModules" :key="module.title" class="feature-card" type="button" @click="selectCreatorModule(module.action, module.title)">
                <span class="feature-card-copy"><strong>{{ module.title }}</strong><small>{{ module.subtitle }}</small></span>
                <span class="feature-card-art" :class="`feature-${module.tone}`"><component :is="module.icon" :size="28" /></span>
              </button>
            </div>
          </section>
        </section>

        <section class="content-section" aria-labelledby="recommend-title">
          <div class="section-heading"><div><h2 id="recommend-title">为你推荐</h2><p>精选模板，快速开始下一张海报</p></div><button class="more-link" type="button" @click="showPhaseNotice('更多模板')">更多 <ChevronRight :size="15" /></button></div>
          <div class="poster-rail">
            <button v-for="poster in posterSamples" :key="poster.title" class="poster-card" type="button" @click="openSample">
              <span class="poster-art" :class="`poster-${poster.tone}`"><span class="poster-mini-label">{{ poster.caption }}</span><strong>{{ poster.title }}</strong><i aria-hidden="true" /></span>
            </button>
          </div>
        </section>

        <section v-if="createdDesign" class="content-section recent-section" aria-labelledby="recent-title">
          <div class="section-heading"><div><h2 id="recent-title">最近设计</h2><p>你刚刚创建的设计稿</p></div><button class="more-link" type="button" @click="showPhaseNotice('设计列表')">更多 <ChevronRight :size="15" /></button></div>
          <button class="recent-design-card" type="button" @click="showPhaseNotice('编辑器')"><span class="recent-design-art"><LayoutDashboard :size="27" /></span><span><strong>{{ createdDesign.name }}</strong><small>版本 {{ createdDesign.currentVersion }}</small></span><ChevronRight :size="18" /></button>
        </section>

        <section id="hotspot-calendar" class="content-section calendar-section" aria-labelledby="calendar-title">
          <div class="section-heading"><div><h2 id="calendar-title">热点日历</h2><p>把握当下灵感，选择适合你的创作场景</p></div><button class="more-link" type="button" @click="showPhaseNotice('热点日历')">更多 <ChevronRight :size="15" /></button></div>
          <div class="calendar-rail">
            <button v-for="(topic, index) in calendarItems" :key="topic.code" class="calendar-card" :class="{ selected: index === 0 }" type="button" @click="showPhaseNotice('热点日历')"><span><strong>{{ topic.title }}</strong><small>{{ topic.startsAt ? topic.startsAt.slice(5, 10).replace('-', '.') : '近期场景' }}</small></span><b v-if="topic.templateCount">{{ topic.templateCount }}<small>款模板</small></b><b v-else>{{ index + 1 }}<small>个场景</small></b></button>
          </div>
          <div class="calendar-posters">
            <button v-for="poster in posterSamples.slice(0, 7)" :key="`calendar-${poster.title}`" class="calendar-poster" type="button" @click="openSample"><span class="poster-art" :class="`poster-${poster.tone}`"><strong>{{ poster.title }}</strong></span><small>{{ poster.caption }}</small></button>
          </div>
        </section>

        <section class="content-section more-templates" aria-labelledby="templates-title">
          <div class="section-heading"><div><h2 id="templates-title">模板库</h2><p>公开发布 · 可直接预览</p></div><span class="section-count">{{ currentTotal }} 个结果</span></div>
          <div class="filter-bar" aria-label="模板筛选">
            <div class="filter-group"><select class="filter-select" :value="query.categoryCode" aria-label="按分类筛选" @change="query.setCategory(($event.target as HTMLSelectElement).value)"><option value="">全部分类</option><option v-for="category in categories" :key="category.code" :value="category.code">{{ category.name }}</option></select><select class="filter-select" :value="query.tagCode" aria-label="按标签筛选" @change="query.setTag(($event.target as HTMLSelectElement).value)"><option value="">全部标签</option><option v-for="tag in home.data?.trendingTags ?? []" :key="tag.code" :value="tag.code">{{ tag.name }}</option></select></div>
            <span v-if="query.categoryCode" class="section-count">{{ categoryName.get(query.categoryCode) ?? query.categoryCode }}</span>
          </div>
          <TemplateGrid :templates="query.data.items" :loading="query.loading" :error="query.error" @select="openTemplate" @retry="query.runSearch" />
          <nav v-if="query.data.total > 0" class="pager" aria-label="模板分页"><button type="button" aria-label="上一页" :disabled="query.page <= 1" @click="query.setPage(query.page - 1)"><ChevronLeft :size="17" /></button><span>{{ pageLabel }}</span><button type="button" aria-label="下一页" :disabled="query.page >= query.totalPages" @click="query.setPage(query.page + 1)"><ChevronRight :size="17" /></button></nav>
        </section>
      </section>
    </main>

    <nav class="bottom-nav" aria-label="移动端导航"><button class="active" type="button" @click="selectMode('templates')"><LayoutDashboard :size="18" />工作台</button><button type="button" @click="selectMode('designs')"><Bookmark :size="18" />我的设计</button><button type="button" aria-label="智能创作（后续阶段）" @click="selectMode('smart')"><Sparkles :size="18" />更多工具</button></nav>

    <TemplateDetailDialog :open="detailOpen" :template="selectedDetail" :loading="detailLoading" @close="detailOpen = false" @use-template="startUseTemplate" />
    <LoginDialog :open="loginOpen" :loading="loginLoading" :error="loginError" @close="loginOpen = false" @login="submitLogin" />
    <CreateDesignDialog :open="createOpen" :template="selectedSummary" :loading="createLoading" @close="createOpen = false" @confirm="confirmCreate" />
    <DesignCreatedDialog :open="createdOpen" :design="createdDesign" @close="createdOpen = false" @continue="showPhaseNotice('编辑器')" />
    <div v-if="logoutOpen" class="confirm-backdrop" role="presentation" @click.self="logoutOpen = false">
      <section class="confirm-dialog" role="dialog" aria-modal="true" aria-labelledby="home-logout-title"><button class="confirm-close" type="button" aria-label="关闭确认框" @click="logoutOpen = false"><X :size="20" /></button><h2 id="home-logout-title">确定要退出登录吗？</h2><div><button class="confirm-secondary" type="button" @click="logoutOpen = false">取消</button><button class="confirm-primary" type="button" @click="confirmLogout">确定</button></div></section>
    </div>
  </div>
</template>
