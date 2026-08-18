<script setup lang="ts">
import { computed } from 'vue'
import type { TemplateSummary, TemplateDetail } from '@/api/types'

const props = defineProps<{
  template: TemplateSummary | TemplateDetail
  compact?: boolean
}>()

const palette = [
  ['#f4d35e', '#ee964b'],
  ['#9ad1d4', '#467599'],
  ['#f7b2bd', '#c36f86'],
  ['#d8e2dc', '#84a98c'],
  ['#d9c2ec', '#8f6bb3'],
]

const colors = computed(() => palette[Math.abs(props.template.id) % palette.length])
const hasCover = computed(() => Boolean(props.template.coverUrl))
const label = computed(() => `${props.template.name}模板预览`)
</script>

<template>
  <div
    class="schema-thumbnail"
    :class="{ compact }"
    :style="{ '--thumb-a': colors[0], '--thumb-b': colors[1] }"
    :aria-label="label"
  >
    <img v-if="hasCover" :src="template.coverUrl ?? undefined" :alt="label" />
    <span v-else class="schema-label">{{ template.name }}</span>
  </div>
</template>

<style scoped>
.schema-thumbnail img {
  position: absolute;
  inset: 0;
  z-index: 2;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.schema-thumbnail.compact { aspect-ratio: 3 / 4; }
</style>
