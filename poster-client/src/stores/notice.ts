import { ref } from 'vue'
import { defineStore } from 'pinia'

export const useNoticeStore = defineStore('notice', () => {
  const message = ref<string | null>(null)
  function show(nextMessage: string) {
    message.value = nextMessage
  }
  function clear() {
    message.value = null
  }
  return { message, show, clear }
})
