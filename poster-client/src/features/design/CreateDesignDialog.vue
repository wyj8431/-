<script setup lang="ts">
import { X, ArrowRight } from 'lucide-vue-next'
import { ref, watch } from 'vue'
import type { TemplateSummary } from '@/api/types'

const props = defineProps<{ open: boolean; template: TemplateSummary | null; loading?: boolean }>()
const emit = defineEmits<{ close: []; confirm: [name: string] }>()
const name = ref('')
watch(() => props.template, (template) => { name.value = template ? `${template.name} · 我的设计` : '' }, { immediate: true })
</script>

<template>
  <div v-if="open" class="dialog-backdrop" role="presentation" @click.self="emit('close')">
    <section class="dialog" role="dialog" aria-modal="true" aria-labelledby="create-design-title">
      <header class="dialog-header">
        <div><p class="eyebrow">Design / new</p><h2 id="create-design-title">确认创建设计稿</h2></div>
        <button class="icon-button" type="button" aria-label="关闭创建确认" @click="emit('close')"><X :size="18" /></button>
      </header>
      <form class="dialog-body login-form" @submit.prevent="emit('confirm', name.trim())">
        <p class="login-copy">将从“{{ template?.name }}”复制一份独立设计稿，原模板不会被修改。</p>
        <label class="form-field">设计稿名称<input v-model="name" required maxlength="80" aria-label="设计稿名称" /></label>
        <button class="dialog-primary login-submit" type="submit" :disabled="loading || !name.trim()">{{ loading ? '创建中…' : '确认创建' }} <ArrowRight :size="16" /></button>
      </form>
    </section>
  </div>
</template>

<style scoped>
.dialog-primary { display: inline-flex; align-items: center; justify-content: center; gap: 8px; }
.login-copy { margin: 0; color: var(--muted); font-size: 12px; line-height: 1.6; }
</style>


