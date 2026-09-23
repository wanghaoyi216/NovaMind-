<script setup lang="ts">
/**
 * OfflineBanner
 * ----------------------------------------------------------------------------
 * 当 navigator.onLine === false 时，固定在顶部展示红色提示条，
 * 显示当前排队待同步的进度请求数量（来自 useBackgroundSync）。
 * 网络恢复后自动消失，并触发一次 flush。
 * ----------------------------------------------------------------------------
 */
import { computed } from 'vue'
import { useOnlineStatus } from '@/composables/useOnlineStatus'
import { useBackgroundSync } from '@/composables/useBackgroundSync'

const { isOnline } = useOnlineStatus()
const { queueLength, flushNow, isSyncing } = useBackgroundSync()

const show = computed(() => !isOnline.value || queueLength.value > 0)
const statusText = computed(() => {
  if (!isOnline.value) {
    return queueLength.value > 0
      ? `当前处于离线模式 · 已有 ${queueLength.value} 条进度在排队中，恢复后自动同步`
      : '当前处于离线模式，部分功能不可用'
  }
  return `正在同步 ${queueLength.value} 条离线进度…`
})
</script>

<template>
  <transition name="offline-slide">
    <div v-if="show" class="offline-banner" role="status" aria-live="polite">
      <div class="banner-inner">
        <span class="dot" :class="{ syncing: isSyncing }"></span>
        <span class="text">{{ statusText }}</span>
        <button
          v-if="isOnline && queueLength > 0"
          class="retry-btn"
          :disabled="isSyncing"
          @click="flushNow()"
        >
          {{ isSyncing ? '同步中…' : '立即同步' }}
        </button>
      </div>
    </div>
  </transition>
</template>

<style lang="scss" scoped>
.offline-banner {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 1100;
  padding: 9px 16px;
  background: var(--nm-invert-bg);
  color: var(--nm-invert-fg);
  border-bottom: 1px solid var(--nm-invert-line);
  box-shadow: var(--nm-sh-3);
  font-size: var(--nm-fs-sm);
  font-weight: 550;
}

.banner-inner {
  max-width: var(--nm-shell);
  margin: 0 auto;
  display: flex;
  align-items: center;
  gap: 10px;
  justify-content: center;
}

.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--nm-danger);
  animation: pulse 1.4s infinite;
}

.dot.syncing {
  animation-duration: 0.8s;
  background: var(--nm-warning);
}

.text {
  letter-spacing: 0.01em;
}

.retry-btn {
  margin-left: 4px;
  padding: 4px 13px;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid var(--nm-invert-line);
  border-radius: var(--nm-r-full);
  color: var(--nm-invert-fg);
  font-size: var(--nm-fs-xs);
  font-weight: 600;
  cursor: pointer;
  transition: background var(--nm-dur-fast) var(--nm-ease);

  &:hover:not(:disabled) {
    background: rgba(255, 255, 255, 0.2);
  }

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
}

@keyframes pulse {
  0% {
    box-shadow: 0 0 0 0 rgba(255, 255, 255, 0.4);
  }
  70% {
    box-shadow: 0 0 0 7px rgba(255, 255, 255, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(255, 255, 255, 0);
  }
}

/* 以动画实现进入态：动画结束态由时间轴保证，不会因转场类残留而不可见 */
.offline-slide-enter-active {
  animation: offline-slide-in 0.26s var(--nm-ease) both;
}

.offline-slide-leave-active {
  animation: offline-slide-in 0.2s var(--nm-ease) reverse both;
}

@keyframes offline-slide-in {
  from {
    opacity: 0;
    transform: translateY(-100%);
  }
  to {
    opacity: 1;
    transform: none;
  }
}
</style>
