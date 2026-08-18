<script setup lang="ts">
import { ArrowUpRight } from 'lucide-vue-next'
import type { TemplateSummary } from '@/api/types'
import SchemaThumbnail from './SchemaThumbnail.vue'

defineProps<{
  template: TemplateSummary
}>()

const emit = defineEmits<{
  select: [template: TemplateSummary]
}>()
</script>

<template>
  <article class="template-card">
    <button
      class="template-card-button"
      type="button"
      :aria-label="`打开${template.name}模板`"
      @click="emit('select', template)"
    >
      <div class="template-artboard">
        <SchemaThumbnail :template="template" />
      </div>
      <div class="template-card-body">
        <h3 class="template-card-title">{{ template.name }}</h3>
        <div class="template-meta">
          <span>{{ template.width }} × {{ template.height }}</span>
          <span class="template-tag">{{ template.tagCodes[0] ?? '精选' }}</span>
          <ArrowUpRight :size="15" aria-hidden="true" />
        </div>
      </div>
    </button>
  </article>
</template>
