<script setup lang="ts">
import { computed, type Component } from 'vue'
import { Document, FolderOpened, Refresh, Search, Warning } from '@element-plus/icons-vue'

interface Props {
  type?: 'search' | 'data' | 'folder' | 'error'
  title?: string
  description?: string
  actionText?: string
  icon?: Component
}

const props = withDefaults(defineProps<Props>(), {
  type: 'data',
  title: '暂无数据',
  description: '没有找到匹配的内容，换个关键词或稍后再试吧',
  actionText: '',
})

const emit = defineEmits<{
  (e: 'action'): void
}>()

const iconComponent = computed<Component>(() => {
  if (props.icon) return props.icon
  switch (props.type) {
    case 'search':
      return Search
    case 'folder':
      return FolderOpened
    case 'error':
      return Warning
    case 'data':
    default:
      return Document
  }
})

</script>

<template>
  <div class="nm-state nm-empty">
    <!-- 插画槽位：提供时替代默认图标位（如空态线稿插画） -->
    <div v-if="$slots.illustration" class="nm-empty__art">
      <slot name="illustration" />
    </div>
    <div v-else class="nm-state__icon">
      <component :is="iconComponent" class="nm-empty__glyph" />
    </div>

    <h3 class="nm-state__title">{{ title }}</h3>
    <p class="nm-state__desc">{{ description }}</p>

    <div v-if="actionText || $slots.action" class="nm-state__actions">
      <slot name="action">
        <button type="button" class="nm-btn nm-btn--primary" @click="emit('action')">
          <Refresh v-if="type === 'error'" />
          {{ actionText }}
        </button>
      </slot>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.nm-empty {
  width: 100%;
}

.nm-empty__art {
  width: 200px;
  max-width: 100%;
  margin-bottom: 4px;

  :deep(svg) {
    width: 100%;
    height: auto;
  }
}

/* 线稿插画为浅色描边，深色主题下降低一点亮度，避免在近黑纸底上过曝 */
[data-theme='dark'] .nm-empty__art {
  opacity: 0.86;
}

.nm-empty__glyph {
  width: 22px;
  height: 22px;
}
</style>
