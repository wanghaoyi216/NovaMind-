<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  name: string
  arguments: string
  content: string
}>()

const args = computed(() => {
  try {
    return JSON.parse(props.arguments)
  } catch {
    return {}
  }
})

const result = computed(() => {
  try {
    return JSON.parse(props.content)
  } catch {
    return props.content
  }
})

function iconFor(name: string): string {
  if (name.includes('search')) return '🔍'
  if (name.includes('read') || name.includes('fetch')) return '📄'
  if (name.includes('code') || name.includes('run') || name.includes('python')) return '⚡'
  if (name.includes('think') || name.includes('reason')) return '🧠'
  if (name.includes('calc') || name.includes('math')) return '📐'
  if (name.includes('image') || name.includes('draw')) return '🎨'
  if (name.includes('store') || name.includes('save')) return '💾'
  if (name.includes('list') || name.includes('file')) return '📂'
  return '🛠'
}

const isList = computed(() => Array.isArray(result.value))
const entries = computed(() => {
  if (isList.value) return (result.value as any[]).slice(0, 6)
  return []
})
const truncated = computed(() => isList.value && (result.value as any[]).length > 6)
</script>

<template>
  <div class="tool-result-card">
    <div class="tool-card-header">
      <span class="tool-icon">{{ iconFor(name) }}</span>
      <span class="tool-name">{{ name }}</span>
    </div>

    <div v-if="Object.keys(args).length > 0" class="tool-card-args">
      <span v-for="(v, k) in args" :key="k" class="arg-tag">
        <span class="arg-key">{{ k }}:</span>
        <span class="arg-val">{{ typeof v === 'string' && v.length > 50 ? v.slice(0, 50) + '...' : typeof v === 'object' ? JSON.stringify(v).slice(0, 50) + (JSON.stringify(v).length > 50 ? '...' : '') : v }}</span>
      </span>
    </div>

    <div v-if="isList" class="tool-card-list">
      <div v-for="(item, i) in entries" :key="i" class="list-row">
        <div v-if="item.title || item.name" class="list-title">{{ item.title || item.name }}</div>
        <div v-else-if="typeof item === 'string'" class="list-text">{{ item.length > 120 ? item.slice(0, 120) + '...' : item }}</div>
        <div v-else class="list-text">{{ JSON.stringify(item).slice(0, 120) }}{{ JSON.stringify(item).length > 120 ? '...' : '' }}</div>
        <div v-if="item.url" class="list-url">
          <a :href="item.url" target="_blank">{{ item.url.length > 50 ? item.url.slice(0, 50) + '...' : item.url }}</a>
        </div>
      </div>
      <div v-if="truncated" class="list-more">+ {{ (result as any[]).length - 6 }} more results</div>
    </div>

    <div v-else class="tool-card-result">{{ typeof result === 'string' ? (result.length > 200 ? result.slice(0, 200) + '...' : result) : JSON.stringify(result).slice(0, 200) }}</div>
  </div>
</template>

<style scoped>
.tool-result-card {
  margin: 8px 0;
  border-radius: var(--nm-r-sm);
  border: 1px solid var(--nm-line);
  background: var(--nm-surface-3);
  color: var(--nm-ink-2);
  overflow: hidden;
  font-size: var(--nm-fs-sm);
}

.tool-card-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: var(--nm-accent-soft);
  border-bottom: 1px solid var(--nm-line);
  font-weight: 600;
  color: var(--nm-ink);
}

.tool-icon { font-size: 14px; }
.tool-name {
  text-transform: capitalize;
  font-family: var(--nm-mono);
  font-size: 12px;
}

.tool-card-args {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  padding: 6px 12px;
  border-bottom: 1px solid var(--nm-line);
}

.arg-tag {
  display: inline-flex;
  gap: 3px;
  padding: 2px 8px;
  border-radius: var(--nm-r-xs);
  background: var(--nm-surface-2);
  border: 1px solid var(--nm-line);
  font-size: 12px;
  white-space: nowrap;
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.arg-key { color: var(--nm-ink-3); font-weight: 500; }
.arg-val { color: var(--nm-cyan); }

.tool-card-list { padding: 6px 12px; }
.list-row {
  padding: 6px 0;
  border-bottom: 1px solid var(--nm-line);
}
.list-row:last-child { border-bottom: none; }

.list-title {
  font-weight: 600;
  color: var(--nm-ink);
  margin-bottom: 2px;
}

.list-text { color: var(--nm-ink-2); line-height: 1.5; }
.list-url { margin-top: 2px; }
.list-url a {
  color: var(--nm-cyan);
  text-decoration: underline;
  text-underline-offset: 2px;
  font-size: 12px;
}

.list-more {
  text-align: center;
  padding: 4px;
  color: var(--nm-ink-3);
  font-size: 12px;
}

.tool-card-result {
  padding: 8px 12px;
  color: var(--nm-ink-2);
  line-height: 1.6;
  font-size: 12px;
  font-family: var(--nm-mono);
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
