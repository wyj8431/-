<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useSessionStore } from '@/stores/session'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const message = ref<string | null>(null)
const restoring = ref(false)

const result = fixedResult(route.query.result)
const returnTo = safeReturnTo(route.query.returnTo)

function fixedResult(value: unknown) {
  return typeof value === 'string' && ['success', 'unbound', 'already_bound', 'cancelled', 'failed', 'expired'].includes(value)
    ? value
    : 'failed'
}

function safeReturnTo(value: unknown) {
  if (typeof value !== 'string' || !value.startsWith('/') || value.includes('//') || value.includes('\\') || value.includes('://') || value.includes('#')) {
    return '/'
  }
  return value
}

function resultMessage(value: string) {
  return {
    unbound: '请先使用手机号登录，再绑定微信账号',
    already_bound: '该微信账号已绑定其他账户',
    cancelled: '已取消微信授权',
    failed: '微信授权失败，请重新尝试',
    expired: '微信授权已过期，请重新发起登录',
  }[value] ?? '微信授权失败，请重新尝试'
}

async function finishSuccessfulLogin() {
  restoring.value = true
  try {
    if (await session.restore()) {
      await router.replace(returnTo)
      return
    }
    message.value = '登录状态恢复失败，请重新登录'
  } catch {
    message.value = '登录状态恢复失败，请重新登录'
  } finally {
    restoring.value = false
  }
}

onMounted(() => {
  if (result === 'success') {
    void finishSuccessfulLogin()
    return
  }
  message.value = resultMessage(result)
})
</script>

<template>
  <main class="wechat-result-page">
    <section class="wechat-result" aria-live="polite">
      <p v-if="restoring">正在恢复登录状态</p>
      <template v-else>
        <h1>微信授权结果</h1>
        <p>{{ message ?? '正在处理授权结果' }}</p>
        <button v-if="message" type="button" @click="router.replace('/')">返回工作台</button>
      </template>
    </section>
  </main>
</template>

<style scoped>
.wechat-result-page { display: grid; min-height: 100vh; place-items: center; padding: 24px; background: #f3f6fb; color: #22314a; }
.wechat-result { width: min(100%, 420px); padding: 36px; border: 1px solid #dce5f1; border-radius: 8px; background: #fff; text-align: center; box-shadow: 0 16px 42px rgba(36, 57, 89, .1); }
.wechat-result h1 { margin: 0 0 16px; font-size: 22px; }.wechat-result p { margin: 0; line-height: 1.65; }.wechat-result button { min-width: 132px; min-height: 42px; margin-top: 28px; border: 0; border-radius: 6px; background: #0877ff; color: #fff; font-size: 15px; }.wechat-result button:hover { background: #0069ed; }
</style>
