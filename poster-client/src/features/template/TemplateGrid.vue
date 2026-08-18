<script setup lang="ts">
import type { TemplateSummary } from '@/api/types'
import TemplateCard from './TemplateCard.vue'

withDefaults(defineProps<{
  templates: TemplateSummary[]
  loading?: boolean
  error?: string | null
}>(), {
  loading: false,
  error: null,
})

const emit = defineEmits<{
  select: [template: TemplateSummary]
  retry: []
}>()
</script>

<template>
  <div v-if="loading" class="template-grid" aria-label="模板加载中">
    <div v-for="index in 8" :key="index" class="template-card" aria-hidden="true">
      <div class="template-artboard"><div class="template-skeleton" /></div>
      <div class="template-card-body"><div class="template-skeleton-line" /><div class="template-skeleton-short" /></div>
    </div>
  </div>
  <div v-else-if="error" class="error-state" role="alert">
    <p>{{ error }}</p>
    <button type="button" @click="emit('retry')">重新加载</button>
  </div>
  <div v-else-if="templates.length === 0" class="empty-state">
    <p>没有找到匹配模板</p>
  </div>
  <div v-else class="template-grid">
    <TemplateCard
      v-for="template in templates"
      :key="template.id"
      :template="template"
      @select="emit('select', $event)"
    />
  </div>
</template>

<style scoped>
.template-skeleton-line,
.template-skeleton-short {
  height: 12px;
  background: #ededed;
}

.template-skeleton-line { width: 70%; margin-bottom: 10px; }
.template-skeleton-short { width: 42%; height: 9px; }
</style>
