<script setup lang="ts">
import { X, ArrowRight } from 'lucide-vue-next'
import type { TemplateDetail } from '@/api/types'
import SchemaThumbnail from './SchemaThumbnail.vue'

const props = withDefaults(defineProps<{
  open: boolean
  template: TemplateDetail | null
  loading?: boolean
  onUseTemplate?: (templateId: number) => void
}>(), { loading: false })

const emit = defineEmits<{
  close: []
  'use-template': [templateId: number]
}>()
</script>

<template>
  <div v-if="open" class="dialog-backdrop" role="presentation" @click.self="emit('close')">
    <section class="dialog" role="dialog" aria-modal="true" aria-labelledby="template-dialog-title">
      <header class="dialog-header">
        <div>
          <p class="eyebrow">Template / detail</p>
          <h2 id="template-dialog-title">{{ template?.name ?? '模板详情' }}</h2>
        </div>
        <button class="icon-button" type="button" aria-label="关闭详情" @click="emit('close')"><X :size="18" /></button>
      </header>
      <div v-if="loading" class="dialog-body"><div class="template-skeleton" /></div>
      <div v-else-if="template" class="dialog-body detail-layout">
        <SchemaThumbnail :template="template" />
        <div class="detail-copy">
          <h3>{{ template.name }}</h3>
          <p>{{ template.width }} × {{ template.height }} px</p>
          <p v-if="template.categoryCode">分类：{{ template.categoryCode }}</p>
          <p>可编辑字段：{{ template.fields.length }} 项</p>
          <ul v-if="template.fields.length" class="detail-fields">
            <li v-for="field in template.fields" :key="field.fieldKey">{{ field.label }}</li>
          </ul>
        </div>
      </div>
      <div v-else class="dialog-body empty-state"><p>模板详情暂不可用</p></div>
      <footer class="dialog-actions">
        <button class="dialog-secondary" type="button" @click="emit('close')">先看看</button>
        <button
          class="dialog-primary"
          type="button"
          :disabled="!template || loading"
          @click="template && (props.onUseTemplate?.(template.id), emit('use-template', template.id))"
        >
          使用此模板 <ArrowRight :size="16" aria-hidden="true" />
        </button>
      </footer>
    </section>
  </div>
</template>

<style scoped>
.dialog-primary { display: inline-flex; align-items: center; gap: 8px; }
.detail-fields { display: grid; gap: 7px; margin: 18px 0 0; padding: 0; color: var(--ink); font-size: 12px; list-style: none; }
.detail-fields li { padding-left: 12px; border-left: 2px solid var(--accent); }
</style>
