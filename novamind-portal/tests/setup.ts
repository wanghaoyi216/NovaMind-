/**
 * Vitest 全局 setup
 * ----------------------------------------------------------------------------
 *  - 引入 fake-indexeddb（覆盖浏览器 IndexedDB API）
 *  - polyfill serviceWorker / SyncManager（在 jsdom 中默认缺失）
 *  - 屏蔽控制台噪音
 * ----------------------------------------------------------------------------
 */
import 'fake-indexeddb/auto'

// 一些 Node 18+ 的全局在 jsdom 中需要补充
if (typeof globalThis.structuredClone === 'undefined') {
  globalThis.structuredClone = (v: any) => JSON.parse(JSON.stringify(v))
}
