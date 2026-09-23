import axios, { AxiosError } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { set, createStore } from 'idb-keyval'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// 失败进度队列（与 SW 中 service-worker.ts 共享同一份 IndexedDB store）
const PROGRESS_STORE = createStore('novamind-pwa', 'pending-progress')

// 网络错误提示去重（5s 窗口内只弹一次）
let lastNetworkToastAt = 0

request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('nova_token')
    if (token) {
      config.headers.Authorization = token
    }
    return config
  },
  (error) => Promise.reject(error)
)

/**
 * 后端统一响应体的成功码。
 * 见 novamind-common `ErrorInfo.Code`：SUCCESS = 200、FAILED = 0。
 * 此前这里判断的是 `code !== 0`，等于把「成功」当失败、把「失败」当成功，
 * 导致所有接口调用都被 reject，页面只能渲染兜底/空态数据。
 */
const SUCCESS_CODE = 200

request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res && typeof res === 'object' && res.code !== undefined && res.code !== SUCCESS_CODE) {
      if (res.code === 401) {
        // 只有「本来带着 token 却被判定未登录」才是会话过期；
        // 未登录访客浏览公开内容（如课程详情）时上游也会回 401，
        // 此时把人踢去登录页反而打断浏览，交给页面自己的错误态/登录引导处理。
        const hadToken = Boolean(localStorage.getItem('nova_token'))
        localStorage.removeItem('nova_token')
        if (hadToken) {
          ElMessage.error('登录已过期，请重新登录')
          router.push('/portal/login')
        }
        return Promise.reject(new Error('Unauthorized'))
      }
      if (res.code === 403) {
        ElMessage.error('权限不足')
        return Promise.reject(new Error('Forbidden'))
      }
      ElMessage.error(res.msg || '请求失败')
      return Promise.reject(new Error(res.msg || '请求失败'))
    }
    return res
  },
  async (error: AxiosError) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('nova_token')
      router.push('/portal/login')
      ElMessage.error('登录已过期，请重新登录')
    } else if (error.config && isProgressEndpoint(error.config.url || '')) {
      // 进度上报失败：写入 IndexedDB 让 SW 后续重试
      try {
        const id = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
        await set(
          id,
          {
            id,
            url: error.config.url || '/learning/progress',
            body: safeParse(error.config.data),
            headers: { Authorization: String(error.config.headers?.Authorization || '') },
            enqueuedAt: Date.now(),
            retries: 0,
            lastError: error.message,
          },
          PROGRESS_STORE
        )
        // 触发 SW 注册 sync
        if ('serviceWorker' in navigator) {
          try {
            const reg = await navigator.serviceWorker.ready
            const regAny = reg as any
            if (regAny.sync && typeof regAny.sync.register === 'function') {
              await regAny.sync.register('novamind-sync-progress')
            } else {
              reg.active?.postMessage({ type: 'FLUSH_PENDING_PROGRESS' })
            }
          } catch {
            // 浏览器不支持 sync
          }
        }
        ElMessage.warning('当前网络不稳定，进度已加入离线队列')
      } catch {
        ElMessage.error(error.message || '网络异常')
      }
    } else {
      // 网络类错误统一为温和提示并做 5s 去重，避免错误弹窗刷屏遮挡页面
      const status = error.response?.status
      const friendly =
        status && status >= 500 ? '服务暂未连接，请稍后再试' : '网络异常，请检查连接后重试'
      const now = Date.now()
      if (now - lastNetworkToastAt > 5000) {
        lastNetworkToastAt = now
        ElMessage.warning(friendly)
      }
      if (import.meta.env.DEV) {
        // eslint-disable-next-line no-console
        console.warn('[request]', error.message)
      }
    }
    return Promise.reject(error)
  }
)

function isProgressEndpoint(url: string): boolean {
  return /\/learning\/progress(\/|$|\?)/.test(url)
}

function safeParse(raw: any): Record<string, unknown> {
  if (!raw) return {}
  if (typeof raw === 'object') return raw
  try {
    return JSON.parse(raw)
  } catch {
    return {}
  }
}

export default request
