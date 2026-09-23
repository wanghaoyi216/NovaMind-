import { onMounted, onUnmounted, readonly, ref } from 'vue'

/**
 * useOnlineStatus
 * ----------------------------------------------------------------------------
 * 监听 navigator.onLine 与 online / offline 事件，提供响应式的 isOnline 状态。
 * 典型用法：
 *   const { isOnline, wasOffline } = useOnlineStatus()
 *   // 顶部显示 offline banner：v-if="!isOnline"
 *   // 网络刚恢复时显示 toast：watch(wasOffline, ...)
 * ----------------------------------------------------------------------------
 */
export function useOnlineStatus() {
  // SSR 守卫
  const isOnline = ref<boolean>(
    typeof navigator !== 'undefined' ? navigator.onLine : true
  )
  // 是否刚刚从离线恢复（用于触发一次性 toast / 自动重试）
  const wasOffline = ref<boolean>(false)
  // 上次离线发生的时刻
  const lastOfflineAt = ref<number | null>(null)

  function handleOnline() {
    if (!isOnline.value) {
      wasOffline.value = true
      // 立刻重置回 false 以便下次再触发
      setTimeout(() => (wasOffline.value = false), 0)
    }
    isOnline.value = true
  }

  function handleOffline() {
    isOnline.value = false
    lastOfflineAt.value = Date.now()
  }

  onMounted(() => {
    if (typeof window === 'undefined') return
    window.addEventListener('online', handleOnline)
    window.addEventListener('offline', handleOffline)
    // 兼容移动端 / PWA 场景下 onLine 不准
    isOnline.value = navigator.onLine
  })

  onUnmounted(() => {
    if (typeof window === 'undefined') return
    window.removeEventListener('online', handleOnline)
    window.removeEventListener('offline', handleOffline)
  })

  return {
    isOnline: readonly(isOnline),
    wasOffline: readonly(wasOffline),
    lastOfflineAt: readonly(lastOfflineAt),
  }
}

export default useOnlineStatus
