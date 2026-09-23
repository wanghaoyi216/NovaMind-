/// <reference lib="webworker" />
/* eslint-disable @typescript-eslint/no-explicit-any */
/**
 * NovaMind Portal - Custom Service Worker
 * ----------------------------------------------------------------------------
 * - injectManifest 模式：workbox 会把 precacheAndRoute / NavigationRoute 等
 *   注入进来，本文件负责 push / sync / IndexedDB 暂存 / AI 章节离线下载。
 * - runtimeCaching 由 vite-plugin-pwa 在编译期生成 self.__WB_MANIFEST 后注入。
 * ----------------------------------------------------------------------------
 */

import { precacheAndRoute, cleanupOutdatedCaches } from 'workbox-precaching'
import { registerRoute, NavigationRoute } from 'workbox-routing'
import {
  NetworkFirst,
  CacheFirst,
  StaleWhileRevalidate,
} from 'workbox-strategies'
import { ExpirationPlugin } from 'workbox-expiration'
import { CacheableResponsePlugin } from 'workbox-cacheable-response'
import { createStore, get as idbGet, set as idbSet, keys as idbKeys } from 'idb-keyval'

// ----------------------------------------------------------------------------
// 1. Precache（由 vite-plugin-pwa 在 build 时注入 __WB_MANIFEST）
// ----------------------------------------------------------------------------
declare const self: ServiceWorkerGlobalScope & {
  __WB_MANIFEST: any
}

cleanupOutdatedCaches()
precacheAndRoute(self.__WB_MANIFEST || [])

// SPA 导航回退：所有未匹配的非 /api 导航请求回退到 /index.html
const handler = new NetworkFirst({
  cacheName: 'novamind-navigation',
  networkTimeoutSeconds: 4,
  plugins: [
    new CacheableResponsePlugin({ statuses: [0, 200] }),
  ],
})
const navigationRoute = new NavigationRoute(handler, {
  // 排除后端接口与 AI 网关
  denylist: [/^\/api/, /^\/ais/, /^\/admin-api/],
})
registerRoute(navigationRoute)

// ----------------------------------------------------------------------------
// 2. 兜底 runtimeCaching（与 vite.config.ts 中配置保持同步，作为冗余）
// ----------------------------------------------------------------------------
registerRoute(
  ({ url, request }) =>
    request.method === 'GET' &&
    (url.pathname.startsWith('/api/') || url.pathname.startsWith('/ais/')),
  new NetworkFirst({
    cacheName: 'novamind-api-get',
    networkTimeoutSeconds: 6,
    plugins: [
      new CacheableResponsePlugin({ statuses: [0, 200] }),
      new ExpirationPlugin({
        maxEntries: 200,
        maxAgeSeconds: 60 * 5, // 5 分钟
      }),
    ],
  })
)

registerRoute(
  ({ request }) =>
    request.destination === 'image' ||
    /\.(?:png|jpg|jpeg|svg|gif|webp|ico)$/.test(new URL(request.url).pathname),
  new CacheFirst({
    cacheName: 'novamind-images',
    plugins: [
      new CacheableResponsePlugin({ statuses: [0, 200] }),
      new ExpirationPlugin({
        maxEntries: 300,
        maxAgeSeconds: 60 * 60 * 24 * 30, // 30 天
      }),
    ],
  })
)

registerRoute(
  ({ request }) =>
    request.destination === 'font' ||
    /\.(?:woff2?|ttf|otf|eot)$/.test(new URL(request.url).pathname),
  new CacheFirst({
    cacheName: 'novamind-fonts',
    plugins: [
      new CacheableResponsePlugin({ statuses: [0, 200] }),
      new ExpirationPlugin({
        maxEntries: 30,
        maxAgeSeconds: 60 * 60 * 24 * 365,
      }),
    ],
  })
)

registerRoute(
  ({ request }) => request.destination === 'style' || request.destination === 'script',
  new StaleWhileRevalidate({
    cacheName: 'novamind-static-assets',
    plugins: [
      new CacheableResponsePlugin({ statuses: [0, 200] }),
      new ExpirationPlugin({
        maxEntries: 200,
        maxAgeSeconds: 60 * 60 * 24 * 30,
      }),
    ],
  })
)

// ----------------------------------------------------------------------------
// 3. IndexedDB 暂存（用于 AI 章节离线下载 + 失败 POST 重试）
// ----------------------------------------------------------------------------
const DB_NAME = 'novamind-pwa'
const STORE_OFFLINE_LESSON = createStore(DB_NAME, 'offline-lessons')   // AI 章节离线包
const STORE_PENDING_PROGRESS = createStore(DB_NAME, 'pending-progress') // 失败进度请求队列

const SYNC_TAG_PROGRESS = 'novamind-sync-progress'
const SYNC_TAG_LESSON = 'novamind-sync-lesson'

// ----------------------------------------------------------------------------
// 4. install / activate：触发 skipWaiting 但等待页面 ready 才接管
// ----------------------------------------------------------------------------
self.addEventListener('install', () => {
  // 立即激活，autoUpdate 模式下由前端 registerSW 控制
  self.skipWaiting()
})

self.addEventListener('activate', (event) => {
  event.waitUntil(self.clients.claim())
})

// ----------------------------------------------------------------------------
// 5. push 事件：接收服务端推送（AI 课程更新 / 学习提醒）
// ----------------------------------------------------------------------------
self.addEventListener('push', (event) => {
  let payload: { title?: string; body?: string; icon?: string; badge?: string; data?: any } = {
    title: 'NovaMind 智星云',
    body: '你有新的学习动态',
  }
  if (event.data) {
    try {
      payload = { ...payload, ...event.data.json() }
    } catch {
      payload.body = event.data.text()
    }
  }

  const title = payload.title || 'NovaMind 智星云'
  const options: NotificationOptions & { renotify?: boolean } = {
    body: payload.body,
    icon: payload.icon || '/pwa-icons/pwa-192x192.png',
    badge: payload.badge || '/pwa-icons/pwa-192x192.png',
    data: payload.data,
    tag: 'novamind-push',
    renotify: true,
  }

  event.waitUntil(self.registration.showNotification(title, options as NotificationOptions))
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const targetUrl = (event.notification.data && event.notification.data.url) || '/portal/home'
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clientsArr) => {
      for (const client of clientsArr) {
        if ('focus' in client) {
          client.navigate(targetUrl)
          return client.focus()
        }
      }
      if (self.clients.openWindow) {
        return self.clients.openWindow(targetUrl)
      }
    })
  )
})

// ----------------------------------------------------------------------------
// 6. message 事件：页面与 SW 通信（AI 章节下载、查询队列长度）
// ----------------------------------------------------------------------------
self.addEventListener('message', (event) => {
  const data = event.data || {}
  const reply = (payload: any) => {
    if (event.ports && event.ports[0]) {
      event.ports[0].postMessage(payload)
    }
  }

  if (data.type === 'SKIP_WAITING') {
    self.skipWaiting()
    return
  }

  if (data.type === 'DOWNLOAD_LESSON') {
    // 异步执行下载流程，把章节内容缓存到 SW 缓存 + IndexedDB
    event.waitUntil(downloadLessonForOffline(data.lessonId, data.url).then(reply, reply))
    return
  }

  if (data.type === 'GET_PENDING_PROGRESS_COUNT') {
    event.waitUntil(
      idbKeys(STORE_PENDING_PROGRESS).then((count: IDBValidKey[]) => reply({ ok: true, count: count.length }))
    )
    return
  }

  if (data.type === 'FLUSH_PENDING_PROGRESS') {
    event.waitUntil(flushPendingProgress().then(
      () => reply({ ok: true }),
      (err) => reply({ ok: false, error: String(err) })
    ))
    return
  }
})

/**
 * 7. AI 章节离线下载
 *    - 把章节内容（HTML/JSON/图片）写入 SW 缓存 + 元数据写入 IndexedDB
 *    - 注册后台 sync 标签，断网时也能继续
 */
async function downloadLessonForOffline(lessonId: string | number, url?: string) {
  const target = url || `/api/learning/lessons/${lessonId}/content`
  const resp = await fetch(target, { credentials: 'include' })
  if (!resp.ok) {
    throw new Error(`Lesson download failed: ${resp.status}`)
  }
  const cloned = resp.clone()
  const body = await resp.json()
  // 写入 IndexedDB（IndexedDB 暂存，IndexedDB 不易被自动清理）
  await idbSet(String(lessonId), { lessonId, body, downloadedAt: Date.now() }, STORE_OFFLINE_LESSON)
  // 注册 background sync：网络恢复后再回写一次，避免服务端最新数据覆盖
  if ('sync' in self.registration) {
    try {
      const reg = self.registration as any
      await reg.sync.register(SYNC_TAG_LESSON)
    } catch {
      // 忽略：浏览器不支持
    }
  }
  return { ok: true, lessonId, bytes: cloned.headers.get('content-length') }
}

// ----------------------------------------------------------------------------
// 8. sync 事件：失败进度请求重试 / 章节增量同步
// ----------------------------------------------------------------------------
self.addEventListener('sync', (event: any) => {
  if (event.tag === SYNC_TAG_PROGRESS) {
    event.waitUntil(flushPendingProgress())
  } else if (event.tag === SYNC_TAG_LESSON) {
    event.waitUntil(syncLessonDelta())
  }
})

async function flushPendingProgress() {
  const allKeys = await idbKeys(STORE_PENDING_PROGRESS)
  if (!allKeys.length) return { ok: true, count: 0 }

  let success = 0
  let fail = 0
  for (const key of allKeys) {
    const payload = await idbGet(key, STORE_PENDING_PROGRESS)
    if (!payload) continue
    try {
      const resp = await fetch('/api/learning/progress', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(payload.headers || {}),
        },
        body: JSON.stringify(payload.body),
        credentials: 'include',
      })
      if (resp.ok) {
        await idbSet(key, undefined as any, STORE_PENDING_PROGRESS).catch(() => {})
        // 通知所有 client 同步成功
        const clientsArr = await self.clients.matchAll({ type: 'window' })
        for (const c of clientsArr) {
          c.postMessage({ type: 'PROGRESS_SYNCED', key, body: payload.body })
        }
        success++
      } else {
        fail++
      }
    } catch {
      fail++
    }
  }
  return { ok: true, success, fail }
}

async function syncLessonDelta() {
  // 简单的增量回写：标记每个章节 dirty=false，等下次用户打开时优先展示本地
  const allKeys = await idbKeys(STORE_OFFLINE_LESSON)
  for (const k of allKeys) {
    const item = await idbGet(k, STORE_OFFLINE_LESSON)
    if (item) {
      item.dirty = false
      await idbSet(k, item, STORE_OFFLINE_LESSON)
    }
  }
  return { ok: true, count: allKeys.length }
}

export {}
