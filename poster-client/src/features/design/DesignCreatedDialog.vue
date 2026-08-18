<script setup lang="ts">
import { Check, X, ArrowRight } from 'lucide-vue-next'
import type { DesignView } from '@/api/types'

defineProps<{ open: boolean; design: DesignView | null }>()

const emit = defineEmits<{ close: []; continue: [] }>()
</script>

<template>
  <div v-if="open" class="dialog-backdrop" role="presentation" @click.self="emit('close')">
    <section class="dialog" role="dialog" aria-modal="true" aria-labelledby="design-created-title">
      <header class="dialog-header">
        <div><p class="eyebrow">Design / saved</p><h2 id="design-created-title">设计稿已创建</h2></div>
        <button class="icon-button" type="button" aria-label="关闭创建提示" @click="emit('close')"><X :size="18" /></button>
      </header>
      <div class="dialog-body">
        <div class="success-mark"><Check :size="22" /></div>
        <p class="design-created-name">{{ design?.name ?? '未命名设计稿' }}</p>
        <p class="design-created-copy">当前阶段先完成模板发现和设计稿创建确认，编辑器将在后续阶段开放。</p>
      </div>
      <footer class="dialog-actions">
        <button class="dialog-secondary" type="button" @click="emit('close')">返回模板中心</button>
        <button class="dialog-primary" type="button" @click="emit('continue')">查看设计稿 <ArrowRight :size="16" /></button>
      </footer>
    </section>
  </div>
</template>

<style scoped>
.dialog-primary { display: inline-flex; align-items: center; gap: 8px; }
.design-created-name { margin: 0; font-size: 16px; font-weight: 700; }
.design-created-copy { max-width: 430px; margin: 10px 0 0; color: var(--muted); font-size: 12px; line-height: 1.6; }
</style>


