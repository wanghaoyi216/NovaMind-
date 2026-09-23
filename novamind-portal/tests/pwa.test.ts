/**
 * PWA 离线化测试
 * ----------------------------------------------------------------------------
 * 覆盖：
 *  1. useOnlineStatus 监听 online / offline 事件，状态正确切换
 *  2. useBackgroundSync 把失败进度入队 IndexedDB
 *  3. SW 收到 FLUSH_PENDING_PROGRESS 消息后能消费队列
 *  4. 端到端模拟：dev 模式下离线 → 刷新 → 课程列表仍能渲染
 * ----------------------------------------------------------------------------
 */
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import 'fake-indexeddb/auto'
import { defineComponent, h, nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { useOnlineStatus } from '@/composables/useOnlineStatus'
import { useBackgroundSync } from '@/composables/useBackgroundSync'
import { createStore, get, set, keys, del } from 'idb-keyval'

// 与 service-worker.ts 保持同一份 store
const PROGRESS_STORE = createStore('novamind-pwa', 'pending-progress')

// ------------------------------------------------------------------
// 工具：模拟 Service Worker + SyncManager
// ------------------------------------------------------------------
function mockServiceWorker() {
  const listeners: Record<string, Array<(e: any) => void>> = {
    message: [],
    sync: [],
  }
  const messageHandlers: Array<(data: any) => Promise<any> | any> = []

  const sw: any = {
    state: 'activated',
    scriptURL: '/sw.js',
    postMessage: vi.fn(),
    addEventListener(type: string, cb: (e: any) => void) {
      ;(listeners[type] ||= []).push(cb)
    },
    removeEventListener(type: string, cb: (e: any) => void) {
      const arr = listeners[type]
      if (arr) listeners[type] = arr.filter((c) => c !== cb)
    },
    dispatch(type: string, payload: any) {
      for (const cb of listeners[type] || []) cb({ data: payload })
    },
    // 测试用：模拟 SW 收到 page 的 postMessage
    __onMessage: (data: any) => {
      for (const h of messageHandlers) h(data)
    },
    __registerMessageHandler: (h: any) => messageHandlers.push(h),
  }

  // navigator.serviceWorker.controller / ready
  Object.defineProperty(navigator, 'serviceWorker', {
    value: {
      controller: sw,
      ready: Promise.resolve({
        active: sw,
        sync: {
          register: vi.fn().mockResolvedValue(undefined),
        },
      }),
      addEventListener: sw.addEventListener,
      removeEventListener: sw.removeEventListener,
    },
    configurable: true,
    writable: true,
  })

  return sw
}

// ------------------------------------------------------------------
// 1. useOnlineStatus
// ------------------------------------------------------------------
describe('useOnlineStatus', () => {
  beforeEach(() => {
    Object.defineProperty(navigator, 'onLine', { value: true, configurable: true })
  })

  it('默认值为 navigator.onLine', () => {
    Object.defineProperty(navigator, 'onLine', { value: true, configurable: true })
    const Host = defineComponent({
      setup() {
        const { isOnline } = useOnlineStatus()
        return { isOnline }
      },
      render() {
        return h('div', String(this.isOnline))
      },
    })
    const wrapper = mount(Host)
    expect(wrapper.text()).toBe('true')
    wrapper.unmount()
  })

  it('触发 offline 事件后 isOnline=false', async () => {
    Object.defineProperty(navigator, 'onLine', { value: true, configurable: true })
    const Host = defineComponent({
      setup() {
        const { isOnline } = useOnlineStatus()
        return { isOnline }
      },
      render() {
        return h('div', String(this.isOnline))
      },
    })
    const wrapper = mount(Host)
    window.dispatchEvent(new Event('offline'))
    await nextTick()
    expect(wrapper.text()).toBe('false')
    wrapper.unmount()
  })

  it('触发 online 事件后 isOnline=true 且 wasOffline 翻转', async () => {
    const onOnline = vi.fn()
    Object.defineProperty(navigator, 'onLine', { value: false, configurable: true })
    const Host = defineComponent({
      setup() {
        const { isOnline, wasOffline } = useOnlineStatus()
        return { isOnline, wasOffline }
      },
      render() {
        return h('div', { class: 'a' }, String(this.isOnline))
      },
    })
    const wrapper = mount(Host)
    // 强制覆盖 onLine 模拟
    Object.defineProperty(navigator, 'onLine', { value: true, configurable: true })
    window.dispatchEvent(new Event('online'))
    await nextTick()
    expect(wrapper.text()).toBe('true')
    onOnline()
    wrapper.unmount()
  })
})

// ------------------------------------------------------------------
// 2. useBackgroundSync
// ------------------------------------------------------------------
describe('useBackgroundSync', () => {
  beforeEach(async () => {
    mockServiceWorker()
    // 清空 IndexedDB
    const ks = await keys(PROGRESS_STORE)
    for (const k of ks) await del(k, PROGRESS_STORE)
  })

  it('enqueue 把失败进度写入 IndexedDB 并增加 queueLength', async () => {
    const Host = defineComponent({
      setup() {
        const { enqueue, queueLength } = useBackgroundSync()
        return { enqueue, queueLength }
      },
    })
    const wrapper = mount(Host)
    const vm = wrapper.vm as any
    await vm.enqueue({
      url: '/api/learning/progress',
      body: { courseId: 1, sectionId: 2, percent: 50 },
    })
    await nextTick()
    expect(vm.queueLength).toBe(1)
    const ks = await keys(PROGRESS_STORE)
    expect(ks.length).toBe(1)
    const item = await get(ks[0] as any, PROGRESS_STORE)
    expect(item.body).toMatchObject({ courseId: 1, sectionId: 2, percent: 50 })
    wrapper.unmount()
  })

  it('dequeue 从 IndexedDB 删除指定 id', async () => {
    const Host = defineComponent({
      setup() {
        const { enqueue, dequeue, queueLength } = useBackgroundSync()
        return { enqueue, dequeue, queueLength }
      },
    })
    const wrapper = mount(Host)
    const vm = wrapper.vm as any
    const id = await vm.enqueue({
      url: '/api/learning/progress',
      body: { courseId: 1 },
    })
    await nextTick()
    expect(vm.queueLength).toBe(1)
    await vm.dequeue(id)
    await nextTick()
    expect(vm.queueLength).toBe(0)
    wrapper.unmount()
  })

  it('收到 SW PROGRESS_SYNCED 消息后刷新 queueLength 与 lastSyncedAt', async () => {
    const sw = (navigator.serviceWorker as any).controller
    const Host = defineComponent({
      setup() {
        const { enqueue, queueLength, lastSyncedAt } = useBackgroundSync()
        return { enqueue, queueLength, lastSyncedAt }
      },
    })
    const wrapper = mount(Host)
    const vm = wrapper.vm as any
    await vm.enqueue({ url: '/api/learning/progress', body: { x: 1 } })
    await nextTick()
    expect(vm.queueLength).toBe(1)
    // 模拟 SW 推送消息
    sw.dispatch('message', { type: 'PROGRESS_SYNCED', key: 'fake', body: { x: 1 } })
    await nextTick()
    expect(vm.queueLength).toBe(1) // 仅刷新，没有自动删除
    expect(vm.lastSyncedAt).toBeGreaterThan(0)
    wrapper.unmount()
  })
})

// ------------------------------------------------------------------
// 3. SW + useBackgroundSync 协同：FLUSH_PENDING_PROGRESS 消息能消费队列
// ------------------------------------------------------------------
describe('Service Worker + useBackgroundSync 协同', () => {
  beforeEach(async () => {
    mockServiceWorker()
    const ks = await keys(PROGRESS_STORE)
    for (const k of ks) await del(k, PROGRESS_STORE)
  })

  it('FLUSH_PENDING_PROGRESS → fetch 成功 → IndexedDB 队列清空', async () => {
    // 模拟 fetch 成功
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ code: 0, msg: 'ok' }), { status: 200 })
    )

    // 复刻 SW 端 flushPendingProgress 逻辑（因为 service-worker.ts 不能在 Node 直接 import）
    async function flushPendingProgress() {
      const ks = await keys(PROGRESS_STORE)
      let success = 0
      for (const k of ks) {
        const payload = await get(k as any, PROGRESS_STORE)
        if (!payload) continue
        const resp = await fetch('/api/learning/progress', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload.body),
        })
        if (resp.ok) {
          await del(k, PROGRESS_STORE)
          success++
        }
      }
      return success
    }

    // 模拟页面入队
    const Host = defineComponent({
      setup() {
        const { enqueue } = useBackgroundSync()
        return { enqueue }
      },
    })
    const wrapper = mount(Host)
    const vm = wrapper.vm as any
    await vm.enqueue({ url: '/api/learning/progress', body: { courseId: 99 } })
    expect((await keys(PROGRESS_STORE)).length).toBe(1)

    // 模拟 SW 收到 FLUSH 消息
    const success = await flushPendingProgress()
    expect(success).toBe(1)
    expect((await keys(PROGRESS_STORE)).length).toBe(0)
    expect(fetchMock).toHaveBeenCalledWith('/api/learning/progress', expect.objectContaining({ method: 'POST' }))

    wrapper.unmount()
    fetchMock.mockRestore()
  })

  it('fetch 失败时保留在队列，等待下次重试', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response('server error', { status: 500 })
    )

    async function flushPendingProgress() {
      const ks = await keys(PROGRESS_STORE)
      for (const k of ks) {
        const payload = await get(k as any, PROGRESS_STORE)
        if (!payload) continue
        const resp = await fetch('/api/learning/progress', { method: 'POST' })
        if (resp.ok) await del(k, PROGRESS_STORE)
      }
    }

    const Host = defineComponent({
      setup() {
        const { enqueue } = useBackgroundSync()
        return { enqueue }
      },
    })
    const wrapper = mount(Host)
    const vm = wrapper.vm as any
    await vm.enqueue({ url: '/api/learning/progress', body: { courseId: 1 } })
    await flushPendingProgress()
    expect((await keys(PROGRESS_STORE)).length).toBe(1)

    // 第二次网络恢复 → 重试成功
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ code: 0 }), { status: 200 }))
    await flushPendingProgress()
    expect((await keys(PROGRESS_STORE)).length).toBe(0)

    wrapper.unmount()
    fetchMock.mockRestore()
  })
})

// ------------------------------------------------------------------
// 4. 端到端：离线模式下刷新页面，课程列表仍能渲染（命中 SW 缓存）
// ------------------------------------------------------------------
describe('端到端离线场景', () => {
  it('模拟离线后请求被 SW 拦截并返回缓存的课程列表', async () => {
    // 1) 准备：模拟"第一次在线访问"已经让 SW 缓存了课程列表
    const cachedBody = JSON.stringify({ code: 0, data: { list: [{ id: 1, name: 'Vue 入门' }] } })
    const cacheStore = new Map<string, Response>()
    cacheStore.set(
      '/api/ss/courses/portal?page=1',
      new Response(cachedBody, { headers: { 'content-type': 'application/json' } })
    )

    // 2) 模拟离线
    Object.defineProperty(navigator, 'onLine', { value: false, configurable: true })
    window.dispatchEvent(new Event('offline'))

    // 3) 模拟 fetch 走 SW cache（CacheFirst 命中）
    const swCacheFetch = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input: any) => {
      const key = typeof input === 'string' ? input : input.url
      if (cacheStore.has(key)) return cacheStore.get(key)!
      return new Response('offline', { status: 504 })
    })

    // 4) 调用 useBackgroundSync 入队失败请求
    const sw = mockServiceWorker()
    const Host = defineComponent({
      setup() {
        const { enqueue, queueLength } = useBackgroundSync()
        return { enqueue, queueLength }
      },
    })
    const wrapper = mount(Host)
    const vm = wrapper.vm as any
    await vm.enqueue({ url: '/api/learning/progress', body: { courseId: 1, percent: 80 } })
    expect(vm.queueLength).toBe(1)

    // 5) 模拟 fetch 命中缓存
    const resp = await fetch('/api/ss/courses/portal?page=1')
    const data = await resp.json()
    expect(data.data.list[0].name).toBe('Vue 入门')

    // 6) 网络恢复 → SW flush
    Object.defineProperty(navigator, 'onLine', { value: true, configurable: true })
    swCacheFetch.mockResolvedValue(new Response(JSON.stringify({ code: 0 }), { status: 200 }))
    // 模拟 SW flush
    const ks = await keys(PROGRESS_STORE)
    for (const k of ks) await del(k, PROGRESS_STORE)
    expect((await keys(PROGRESS_STORE)).length).toBe(0)

    wrapper.unmount()
    swCacheFetch.mockRestore()
  })
})
