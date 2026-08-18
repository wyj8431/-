import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { Router } from 'vue-router'
import { searchTemplates, type TemplateSearchParams } from '@/api/templates'
import type { TemplatePage } from '@/api/types'

type SearchLoader = (params: TemplateSearchParams, signal?: AbortSignal) => Promise<TemplatePage>

export const useTemplateQueryStore = defineStore('template-query', () => {
  const keyword = ref('')
  const categoryCode = ref('')
  const tagCode = ref('')
  const page = ref(1)
  const pageSize = ref(24)
  const data = ref<TemplatePage>({ items: [], page: 1, pageSize: 24, total: 0 })
  const loading = ref(false)
  const error = ref<string | null>(null)
  const router = ref<Router | null>(null)
  let timer: ReturnType<typeof setTimeout> | undefined
  let controller: AbortController | undefined
  let loader: SearchLoader = searchTemplates

  const totalPages = computed(() => Math.max(1, Math.ceil(data.value.total / pageSize.value)))

  function bindRouter(nextRouter: Router) {
    router.value = nextRouter
    const query = nextRouter.currentRoute.value.query
    keyword.value = typeof query.keyword === 'string' ? query.keyword : ''
    categoryCode.value = typeof query.categoryCode === 'string' ? query.categoryCode : ''
    tagCode.value = typeof query.tagCode === 'string' ? query.tagCode : ''
    page.value = Number(query.page) > 0 ? Number(query.page) : 1
  }

  function setSearchLoader(nextLoader: SearchLoader) {
    loader = nextLoader
  }

  async function syncRoute() {
    if (!router.value) return
    const query: Record<string, string> = { page: String(page.value) }
    if (keyword.value) query.keyword = keyword.value
    if (categoryCode.value) query.categoryCode = categoryCode.value
    if (tagCode.value) query.tagCode = tagCode.value
    await router.value.replace({ query })
  }

  async function runSearch() {
    controller?.abort()
    controller = new AbortController()
    loading.value = true
    error.value = null
    try {
      data.value = await loader({
        keyword: keyword.value || undefined,
        categoryCode: categoryCode.value || undefined,
        tagCode: tagCode.value || undefined,
        page: page.value,
        pageSize: pageSize.value,
      }, controller.signal)
    } catch (cause) {
      if (!(cause instanceof DOMException && cause.name === 'AbortError')) {
        error.value = cause instanceof Error ? cause.message : '模板加载失败，请重试'
      }
    } finally {
      loading.value = false
    }
  }

  function scheduleSearch() {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => void runSearch(), 350)
  }

  async function setKeyword(value: string) {
    keyword.value = value.trim()
    page.value = 1
    await syncRoute()
    scheduleSearch()
  }

  async function setCategory(value: string) {
    categoryCode.value = value
    page.value = 1
    await syncRoute()
    scheduleSearch()
  }

  async function setTag(value: string) {
    tagCode.value = value
    page.value = 1
    await syncRoute()
    scheduleSearch()
  }

  async function setPage(value: number) {
    page.value = Math.min(Math.max(1, value), totalPages.value)
    await syncRoute()
    scheduleSearch()
  }

  return {
    keyword,
    categoryCode,
    tagCode,
    page,
    pageSize,
    data,
    loading,
    error,
    totalPages,
    bindRouter,
    setSearchLoader,
    runSearch,
    setKeyword,
    setCategory,
    setTag,
    setPage,
  }
})
