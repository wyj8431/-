<script setup lang="ts">
import { X, LogIn } from 'lucide-vue-next'
import { ref } from 'vue'

withDefaults(defineProps<{
  open: boolean
  loading?: boolean
  error?: string | null
}>(), { loading: false, error: null })

const emit = defineEmits<{
  close: []
  login: [payload: { phone: string; verificationCode: string }]
}>()

const phone = ref('')
const verificationCode = ref('')
</script>

<template>
  <div v-if="open" class="dialog-backdrop" role="presentation" @click.self="emit('close')">
    <section class="dialog login-dialog" role="dialog" aria-modal="true" aria-labelledby="login-dialog-title">
      <header class="dialog-header">
        <div>
          <p class="eyebrow">Account / sign in</p>
          <h2 id="login-dialog-title">登录后继续</h2>
        </div>
        <button class="icon-button" type="button" aria-label="关闭登录" @click="emit('close')"><X :size="18" /></button>
      </header>
      <form class="dialog-body login-form" @submit.prevent="emit('login', { phone, verificationCode })">
        <p class="login-copy">登录后会自动恢复这一次模板选择，不会丢失当前浏览位置。</p>
        <label class="form-field">
          手机号
          <input v-model="phone" name="phone" inputmode="tel" autocomplete="tel" placeholder="请输入手机号" required />
        </label>
        <label class="form-field">
          验证码
          <input v-model="verificationCode" name="verificationCode" inputmode="numeric" autocomplete="one-time-code" placeholder="请输入验证码" required />
        </label>
        <p v-if="error" class="form-error" role="alert">{{ error }}</p>
        <button class="dialog-primary login-submit" type="submit" :disabled="loading">
          <LogIn :size="16" aria-hidden="true" />
          {{ loading ? '登录中…' : '登录并继续' }}
        </button>
      </form>
    </section>
  </div>
</template>

<style scoped>
.login-dialog { width: min(100%, 460px); }
.login-copy { margin: 0; color: var(--muted); font-size: 12px; line-height: 1.6; }
.login-submit { justify-content: center; margin-top: 5px; }
</style>
