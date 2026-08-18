import { ref } from 'vue'
import { defineStore } from 'pinia'
import { loadHome } from '@/api/home'
import type { HomePayload } from '@/api/types'

export const useHomeStore = defineStore('home', () => {
  const data = ref<HomePayload | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function load() {
    loading.value = true
    error.value = null
    try {
      data.value = await loadHome()
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : '首页加载失败，请重试'
    } finally {
      loading.value = false
    }
  }

  return { data, loading, error, load }
})
