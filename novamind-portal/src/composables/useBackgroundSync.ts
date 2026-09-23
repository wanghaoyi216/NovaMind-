import { onMounted, onUnmounted, readonly, ref } from 'vue'
import { keys, set, del, createStore } from 'idb-keyval'

/**
 * useBackgroundSync
 * ----------------------------------------------------------------------------
 * 把失败的 POST /api/learning/progress 请求暂存到 IndexedDB（idb-keyval），
 * 等待 Service Worker 触发 sync 事件时自动重试。
 *
 * 关键点：
 *  - 写入用 idb-keyval 的 store，保证和 SW 端 store 复用同一份数据
 *  - 注册 background sync（若浏览器支持）；不支持时回退到 online 事件轮询
 *  - 监听来自 SW 的 PROGRESS_SYNCED 消息实时更新队列长度
 * ----------------------------------------------------------------------------
 */
const PROGRESS_STORE = createStore('novamind-pwa', 'pending-progress')

export interface QueuedProgress {
  id: string
  url: string
  body: Record<string, unknown>
  headers?: Record<string, string>
  enqueuedAt: number
  retries: number
  lastError?: string
}

export function useBackgroundSync() {
  const queueLength = ref<number>(0)
  const lastSyncedAt = ref<number | null>(null)
  const isSyncing = ref<boolean>(false)

  async function refreshQueueLength() {
    try {
      const ks = await keys(PROGRESS_STORE)
      queueLength.value = ks.length
    } catch {
      queueLength.value = 0
    }
  }

  /**
   * 把一个失败的进度提交加入队列
   */
  async function enqueue(payload: Omit<QueuedProgress, 'id' | 'enqueuedAt' | 'retries'>) {
    const item: QueuedProgress = {
      ...payload,
      id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      enqueuedAt: Date.now(),
      retries: 0,
    }
    await set(item.id, item, PROGRESS_STORE)
    await refreshQueueLength()

    // 让 SW 注册 sync 事件
    if (typeof navigator !== 'undefined' && 'serviceWorker' in navigator) {
      try {
        const reg = await navigator.serviceWorker.ready
        const regAny = reg as any
        if (regAny.sync && typeof regAny.sync.register === 'function') {
          await regAny.sync.register('novamind-sync-progress')
        } else {
          // 后备：主动通知 SW 立即 flush
          reg.active?.postMessage({ type: 'FLUSH_PENDING_PROGRESS' })
        }
      } catch {
        // 忽略：浏览器不支持 background sync
      }
    }
    return item.id
  }

  /**
   * 主动从队列中移除某条（用户撤回、或者已经确认服务端落库）
   */
  async function dequeue(id: string) {
    await del(id, PROGRESS_STORE)
    await refreshQueueLength()
  }

  /**
   * 触发立即重试（用户点 "立即同步"）
   */
  async function flushNow() {
    if (typeof navigator === 'undefined' || !('serviceWorker' in navigator)) {
      return { ok: false, reason: 'no-sw' }
    }
    isSyncing.value = true
    try {
      const reg = await navigator.serviceWorker.ready
      reg.active?.postMessage({ type: 'FLUSH_PENDING_PROGRESS' })
      // 给 SW 一点处理时间
      await new Promise((r) => setTimeout(r, 300))
      await refreshQueueLength()
      lastSyncedAt.value = Date.now()
      return { ok: true }
    } finally {
      isSyncing.value = false
    }
  }

  function handleSWMessage(event: MessageEvent) {
    const data = event.data
    if (!data) return
    if (data.type === 'PROGRESS_SYNCED') {
      // 一条同步完成
      refreshQueueLength()
      lastSyncedAt.value = Date.now()
    }
  }

  function handleOnline() {
    if (queueLength.value > 0) {
      flushNow()
    }
  }

  onMounted(() => {
    refreshQueueLength()
    if (typeof navigator !== 'undefined' && 'serviceWorker' in navigator) {
      navigator.serviceWorker.addEventListener('message', handleSWMessage)
    }
    if (typeof window !== 'undefined') {
      window.addEventListener('online', handleOnline)
    }
  })

  onUnmounted(() => {
    if (typeof navigator !== 'undefined' && 'serviceWorker' in navigator) {
      navigator.serviceWorker.removeEventListener('message', handleSWMessage)
    }
    if (typeof window !== 'undefined') {
      window.removeEventListener('online', handleOnline)
    }
  })

  return {
    queueLength: readonly(queueLength),
    lastSyncedAt: readonly(lastSyncedAt),
    isSyncing: readonly(isSyncing),
    enqueue,
    dequeue,
    flushNow,
    refreshQueueLength,
  }
}

export default useBackgroundSync
