<script setup lang="ts">
import { LayoutTemplate, FolderOpen, Sparkles } from 'lucide-vue-next'

const emit = defineEmits<{
  select: [mode: 'templates' | 'designs' | 'smart']
}>()

const tabs = [
  { mode: 'templates' as const, label: '模板中心', icon: LayoutTemplate },
  { mode: 'designs' as const, label: '我的设计', icon: FolderOpen },
  { mode: 'smart' as const, label: '智能创作', icon: Sparkles },
]

defineProps<{
  active: 'templates' | 'designs' | 'smart'
}>()
</script>

<template>
  <nav class="mode-tabs" aria-label="工作台模式">
    <button
      v-for="tab in tabs"
      :key="tab.mode"
      class="mode-tab"
      :class="{ active: active === tab.mode }"
      type="button"
      :aria-label="tab.mode === 'smart' ? '智能创作（后续阶段）' : tab.label"
      @click="emit('select', tab.mode)"
    >
      <component :is="tab.icon" :size="15" aria-hidden="true" />
      {{ tab.label }}
    </button>
  </nav>
</template>

<style scoped>
.mode-tab { display: inline-flex; align-items: center; gap: 7px; }
</style>

