import { AxiosError } from 'axios'

/**
 * 把底层错误转换成面向用户的中文说明。
 *
 * 直接把 `error.message` 渲染到界面上会暴露 "Request failed with status code 500"
 * 这类实现细节；这里统一收敛为可读的原因描述，并保留排查用的细节供日志使用。
 */
export function friendlyErrorMessage(error: unknown, fallback = '请求失败，请稍后重试'): string {
  if (error instanceof AxiosError) {
    const status = error.response?.status
    if (error.code === 'ECONNABORTED' || /timeout/i.test(error.message)) {
      return '请求超时，请检查网络后重试'
    }
    if (!error.response) {
      return '无法连接到服务，请确认后端已启动'
    }
    if (status === 401) return '登录状态已失效，请重新登录'
    if (status === 403) return '当前账号没有访问权限'
    if (status === 404) return '请求的数据不存在'
    if (status && status >= 500) return '服务暂时不可用，请稍后重试'
    if (status && status >= 400) return '请求参数有误，请调整后重试'
    return fallback
  }

  if (error instanceof Error) {
    // 网络层错误（axios 之外的 fetch / 解析失败）
    if (/network|failed to fetch/i.test(error.message)) {
      return '无法连接到服务，请确认后端已启动'
    }
    // 后端返回的业务错误信息通常已经是中文，可以直接展示
    if (/[\u4e00-\u9fa5]/.test(error.message)) {
      return error.message
    }
  }

  return fallback
}
